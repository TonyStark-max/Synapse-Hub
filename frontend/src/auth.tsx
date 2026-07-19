import React, { createContext, useContext, useState, useEffect } from 'react';
import * as Clerk from '@clerk/clerk-react';
import { Shield, LogOut } from 'lucide-react';

// Detect if real Clerk keys are configured
const isRealClerkConfigured = 
  import.meta.env.VITE_CLERK_PUBLISHABLE_KEY && 
  !import.meta.env.VITE_CLERK_PUBLISHABLE_KEY.startsWith('pk_test_dummy');

// Context for mock authentication
interface MockUser {
  id: string;
  email: string;
  name: string;
  role: 'ADMIN' | 'MEMBER';
}

interface MockAuthContextType {
  user: MockUser | null;
  login: (email: string, name: string) => void;
  logout: () => void;
  isLoaded: boolean;
}

const MockAuthContext = createContext<MockAuthContextType | null>(null);

export const MockAuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<MockUser | null>(null);
  const [isLoaded, setIsLoaded] = useState(false);

  useEffect(() => {
    const saved = localStorage.getItem('synapse_mock_user');
    if (saved) {
      try {
        setUser(JSON.parse(saved));
      } catch (e) {
        localStorage.removeItem('synapse_mock_user');
      }
    }
    setIsLoaded(true);
  }, []);

  const login = (email: string, name: string) => {
    // Determine system administrator email
    const role = email.toLowerCase() === 'lostg826@gmail.com' ? 'ADMIN' : 'MEMBER';
    // Stable user ID mapping based on email prefix to ensure user ID persistency
    const cleanPrefix = email.split('@')[0].replace(/[^a-zA-Z0-9]/g, '');
    const id = `user_mock_${cleanPrefix || 'visitor'}`;
    
    const mockUser: MockUser = { id, email, name, role };
    localStorage.setItem('synapse_mock_user', JSON.stringify(mockUser));
    setUser(mockUser);
  };

  const logout = () => {
    localStorage.removeItem('synapse_mock_user');
    setUser(null);
  };

  return (
    <MockAuthContext.Provider value={{ user, login, logout, isLoaded }}>
      {children}
    </MockAuthContext.Provider>
  );
};

// Custom helper helper to encode base64 JWT payload
function generateMockToken(user: MockUser): string {
  const header = btoa(JSON.stringify({ alg: "none", typ: "JWT" }));
  const payload = btoa(JSON.stringify({
    sub: user.id,
    email: user.email,
    name: user.name
  }));
  return `${header}.${payload}.signature`;
}

// -------------------------------------------------------------
// RE-EXPORT OR MOCK CLERK WRAPPERS
// -------------------------------------------------------------

export const useAuth = () => {
  if (isRealClerkConfigured) {
    return Clerk.useAuth();
  }
  
  const ctx = useContext(MockAuthContext);
  if (!ctx) throw new Error("useAuth must be used inside MockAuthProvider");
  
  const getToken = async () => {
    if (!ctx.user) return null;
    return generateMockToken(ctx.user);
  };

  return {
    isLoaded: ctx.isLoaded,
    orgId: null,
    orgRole: null,
    getToken
  };
};

export const useUser = () => {
  if (isRealClerkConfigured) {
    return Clerk.useUser();
  }

  const ctx = useContext(MockAuthContext);
  if (!ctx) throw new Error("useUser must be used inside MockAuthProvider");

  return {
    isLoaded: ctx.isLoaded,
    user: ctx.user ? {
      id: ctx.user.id,
      primaryEmailAddress: { emailAddress: ctx.user.email },
      fullName: ctx.user.name
    } : null
  };
};

export const SignedIn: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  if (isRealClerkConfigured) {
    return <Clerk.SignedIn>{children}</Clerk.SignedIn>;
  }

  const ctx = useContext(MockAuthContext);
  if (!ctx) return null;
  return ctx.user ? <>{children}</> : null;
};

export const SignedOut: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  if (isRealClerkConfigured) {
    return <Clerk.SignedOut>{children}</Clerk.SignedOut>;
  }

  const ctx = useContext(MockAuthContext);
  if (!ctx) return null;
  return !ctx.user ? <>{children}</> : null;
};

export const SignInButton: React.FC<{ children: React.ReactElement, mode?: 'modal' | 'redirect' }> = ({ children }) => {
  if (isRealClerkConfigured) {
    return <Clerk.SignInButton>{children}</Clerk.SignInButton>;
  }

  const [showModal, setShowModal] = useState(false);
  const [email, setEmail] = useState('');
  const [name, setName] = useState('');
  const ctx = useContext(MockAuthContext);

  const handleSignIn = (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !name) return;
    ctx?.login(email, name);
    setShowModal(false);
  };

  return (
    <>
      {React.cloneElement(children, { onClick: () => setShowModal(true) } as any)}
      {showModal && (
        <div style={{
          position: 'fixed',
          top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0,0,0,0.8)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 9999,
          backdropFilter: 'blur(4px)'
        }}>
          <div className="card" style={{ width: '400px', padding: '2rem', display: 'flex', flexDirection: 'column', gap: '1.5rem', animation: 'scale-in 0.2s ease-out' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              <div className="welcome-icon-box" style={{ width: '40px', height: '40px', background: 'var(--accent-color)', color: '#fff' }}>
                <Shield size={20} />
              </div>
              <div>
                <h3 style={{ margin: 0, fontWeight: 700, color: 'var(--text-color)' }}>Sandbox Sign-In</h3>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Synapse Mock Auth Bypass</span>
              </div>
            </div>
            
            <form onSubmit={handleSignIn} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
                <label style={{ fontSize: '0.8rem', fontWeight: 600 }}>Full Name</label>
                <input 
                  type="text" 
                  value={name} 
                  onChange={(e) => setName(e.target.value)} 
                  placeholder="e.g. Somu Thupakula" 
                  className="input" 
                  required
                />
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
                <label style={{ fontSize: '0.8rem', fontWeight: 600 }}>Email Address</label>
                <input 
                  type="email" 
                  value={email} 
                  onChange={(e) => setEmail(e.target.value)} 
                  placeholder="e.g. somuthupakula983@gmail.com" 
                  className="input" 
                  required
                />
                <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', lineHeight: 1.4 }}>
                  💡 Use <strong>lostg826@gmail.com</strong> to sign in as the System Admin.
                </span>
              </div>

              <div style={{ display: 'flex', gap: '1rem', marginTop: '0.5rem' }}>
                <button type="submit" className="btn btn-primary" style={{ flex: 1 }}>Sign In</button>
                <button type="button" onClick={() => setShowModal(false)} className="btn btn-secondary" style={{ flex: 1 }}>Cancel</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
};

export const UserButton: React.FC<{ afterSignOutUrl?: string }> = () => {
  if (isRealClerkConfigured) {
    return <Clerk.UserButton afterSignOutUrl="/" />;
  }

  const ctx = useContext(MockAuthContext);
  const [isOpen, setIsOpen] = useState(false);

  if (!ctx || !ctx.user) return null;

  return (
    <div style={{ position: 'relative' }}>
      <button 
        onClick={() => setIsOpen(!isOpen)}
        style={{
          width: '36px', height: '36px',
          borderRadius: '50%',
          background: 'var(--accent-color)',
          color: '#fff',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontWeight: 700,
          fontSize: '0.9rem',
          border: 'none',
          cursor: 'pointer'
        }}
      >
        {ctx.user.name.charAt(0).toUpperCase()}
      </button>

      {isOpen && (
        <div className="card" style={{
          position: 'absolute',
          top: '42px', right: 0,
          width: '240px',
          padding: '1rem',
          zIndex: 999,
          display: 'flex',
          flexDirection: 'column',
          gap: '0.8rem',
          boxShadow: '0 10px 25px rgba(0,0,0,0.5)',
          animation: 'fade-in 0.15s ease-out'
        }}>
          <div>
            <p style={{ fontWeight: 700, fontSize: '0.85rem', margin: 0 }}>{ctx.user.name}</p>
            <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', margin: 0 }}>{ctx.user.email}</p>
            <span style={{ 
              display: 'inline-block',
              fontSize: '0.6rem',
              fontWeight: 700,
              padding: '0.2rem 0.5rem',
              borderRadius: '4px',
              background: ctx.user.role === 'ADMIN' ? 'rgba(239, 68, 68, 0.15)' : 'rgba(59, 130, 246, 0.15)',
              color: ctx.user.role === 'ADMIN' ? '#ef4444' : '#3b82f6',
              marginTop: '0.4rem'
            }}>
              {ctx.user.role}
            </span>
          </div>
          <hr style={{ border: 'none', borderBottom: '1px solid var(--card-border)', margin: 0 }} />
          <button 
            onClick={() => {
              ctx.logout();
              setIsOpen(false);
            }}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
              width: '100%',
              background: 'none',
              border: 'none',
              color: '#ef4444',
              cursor: 'pointer',
              fontSize: '0.8rem',
              fontWeight: 600,
              padding: '0.25rem 0'
            }}
          >
            <LogOut size={16} /> Sign Out
          </button>
        </div>
      )}
    </div>
  );
};
