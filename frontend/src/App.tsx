import React, { useState, useEffect } from 'react';
import { 
  Lightbulb, 
  Plus, 
  User as UserIcon, 
  ArrowUp, 
  ArrowDown, 
  MessageSquare, 
  Users, 
  Shield, 
  AlertCircle, 
  Check, 
  Clock, 
  CornerDownRight, 
  Sun,
  Moon,
  CheckCircle,
  XCircle
} from 'lucide-react';
import { 
  SignedIn, 
  SignedOut, 
  SignInButton, 
  UserButton, 
  useAuth,
  useUser
} from './auth';
import { createClient } from '@supabase/supabase-js';

// Setup Supabase Realtime Client (fallbacks to mock when local development keys are mock)
const supabaseUrl = import.meta.env.VITE_SUPABASE_URL || 'https://mock-supabase.supabase.co';
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY || 'mock-key';
const supabase = createClient(supabaseUrl, supabaseAnonKey);

const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';

interface Idea {
  id: number;
  title: string;
  description: string;
  tag: string;
  status: string;
  orgId: string;
  userId: string;
  userName?: string;
  createdAt: string;
  upvotesCount: number;
  downvotesCount: number;
  hotScore: number;
}

interface Comment {
  id: number;
  ideaId: number;
  parentCommentId: number | null;
  userId: string;
  userName?: string;
  orgId: string;
  content: string;
  createdAt: string;
}

interface ActiveOrg {
  id: string;
  name: string;
  inviteCode: string;
}

interface OrgRequest {
  id: number;
  orgId: string;
  name: string;
  description: string;
  requesterId: string;
  requesterEmail: string;
  requesterName: string;
  status: string;
  createdAt: string;
}

export default function App() {
  // Theme State (Dark mode by default)
  const [theme, setTheme] = useState<'dark' | 'light'>('dark');

  // Clerk Auth Hooks
  const { isLoaded, getToken } = useAuth();
  const { user } = useUser();
  const [dbUser, setDbUser] = useState<{ id: string, email: string, name: string, role: string, orgId: string | null } | null>(null);

  const orgId = dbUser?.orgId || null;
  const orgRole = dbUser?.role || null;
  const activeRole = orgRole === 'ADMIN' ? 'ADMIN' : orgRole === 'MEMBER' ? 'MEMBER' : '';
  const userEmail = user?.primaryEmailAddress?.emailAddress;
  const isSystemAdmin = userEmail === 'lostg826@gmail.com';

  // Ideas & Feed State
  const [ideas, setIdeas] = useState<Idea[]>([]);
  const [loading, setLoading] = useState(false);
  const [sortBy, setSortBy] = useState('hot');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // New Idea Form State
  const [newTitle, setNewTitle] = useState('');
  const [newDesc, setNewDesc] = useState('');
  const [newTag, setNewTag] = useState('Feature');

  // Comment & replies states
  const [commentInputs, setCommentInputs] = useState<{ [key: number]: string }>({});
  const [replyInputs, setReplyInputs] = useState<{ [key: number]: string }>({});
  const [commentsByIdea, setCommentsByIdea] = useState<{ [key: number]: Comment[] }>({});
  const [expandedComments, setExpandedComments] = useState<{ [key: number]: boolean }>({});

  // Workspace Discovery & Join flows
  const [activeOrgs, setActiveOrgs] = useState<ActiveOrg[]>([]);
  const [pendingRequests, setPendingRequests] = useState<OrgRequest[]>([]);
  const currentOrgName = activeOrgs.find(o => o.id === orgId)?.name || orgId || '';
  
  // Organization Request Form State
  const [reqOrgId, setReqOrgId] = useState('');
  const [reqOrgName, setReqOrgName] = useState('');
  const [reqOrgDesc, setReqOrgDesc] = useState('');

  // Toggle theme
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  // Fetch PostgreSQL database profile on login
  useEffect(() => {
    if (!isLoaded) return;
    fetchDbProfile();
  }, [isLoaded, userEmail]);

  const toggleTheme = () => {
    setTheme(prev => prev === 'dark' ? 'light' : 'dark');
  };

  // Load ideas if org is selected, otherwise load active workspaces to join
  useEffect(() => {
    if (!isLoaded) return;
    if (orgId) {
      fetchIdeas();
      fetchPendingRequests();
    } else {
      setIdeas([]);
      fetchActiveOrganizations();
      fetchPendingRequests(); // Let system administrators approve requests even if not switched
    }
    setErrorMessage(null);
    setSuccessMessage(null);
  }, [orgId, sortBy, isLoaded, userEmail]);

  // Realtime changes hook
  useEffect(() => {
    if (!orgId || supabaseAnonKey === 'mock-key') return;

    const channel = supabase
      .channel(`ideas-changes-${orgId}`)
      .on(
        'postgres_changes',
        {
          event: '*',
          schema: 'public',
          table: 'ideas',
          filter: `org_id=eq.${orgId}`
        },
        () => {
          fetchIdeas();
        }
      )
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
  }, [orgId]);

  const getHeaders = async () => {
    const token = await getToken({ template: 'synapse' });
    return {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token || ''}`
    };
  };

  const fetchDbProfile = async () => {
    try {
      const headers = await getHeaders();
      const res = await fetch(`${BACKEND_URL}/api/organizations/users/me`, {
        headers
      });
      if (res.ok) {
        const data = await res.json();
        setDbUser(data);
      }
    } catch (e) {
      console.error("Error loading DB user profile:", e);
    }
  };

  const leaveWorkspace = async () => {
    try {
      const headers = await getHeaders();
      const res = await fetch(`${BACKEND_URL}/api/organizations/leave`, {
        method: 'POST',
        headers
      });
      if (res.ok) {
        setSuccessMessage('Exited workspace.');
        setErrorMessage(null);
        fetchDbProfile();
      } else {
        const err = await res.text();
        setErrorMessage(err);
        setSuccessMessage(null);
      }
    } catch (e) {
      setErrorMessage('Failed to leave workspace.');
      setSuccessMessage(null);
    }
  };

  const fetchActiveOrganizations = async () => {
    try {
      const headers = await getHeaders();
      const res = await fetch(`${BACKEND_URL}/api/organizations/active`, {
        headers
      });
      if (res.ok) {
        const data = await res.json();
        setActiveOrgs(data);
      }
    } catch (e) {
      console.error("Error loading active orgs:", e);
    }
  };

  const fetchPendingRequests = async () => {
    try {
      const headers = await getHeaders();
      const res = await fetch(`${BACKEND_URL}/api/organizations/requests`, {
        headers
      });
      if (res.ok) {
        const data = await res.json();
        setPendingRequests(data);
      }
    } catch (e) {
      console.error("Error loading pending requests:", e);
    }
  };

  const fetchIdeas = async () => {
    if (!orgId) return;
    setLoading(true);
    try {
      const headers = await getHeaders();
      const res = await fetch(`${BACKEND_URL}/api/ideas?sortBy=${sortBy}`, {
        headers
      });
      if (res.ok) {
        const data = await res.json();
        setIdeas(data);
      } else {
        const err = await res.text();
        setErrorMessage(`Failed to load ideas: ${err}`);
      }
    } catch (e: any) {
      setErrorMessage(`Error: ${e.message || e}`);
    } finally {
      setLoading(false);
    }
  };

  const submitIdea = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTitle.trim() || !newDesc.trim()) return;

    try {
      const headers = await getHeaders();
      const res = await fetch(`${BACKEND_URL}/api/ideas`, {
        method: 'POST',
        headers,
        body: JSON.stringify({
          title: newTitle,
          description: newDesc,
          tag: newTag
        })
      });

      if (res.ok) {
        setNewTitle('');
        setNewDesc('');
        setNewTag('Feature');
        setSuccessMessage('Idea submitted successfully!');
        setErrorMessage(null);
        fetchIdeas();
      } else {
        const err = await res.text();
        setErrorMessage(err);
        setSuccessMessage(null);
      }
    } catch (e) {
      setErrorMessage('Failed to submit idea.');
      setSuccessMessage(null);
    }
  };

  const submitWorkspaceRequest = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reqOrgId.trim() || !reqOrgName.trim()) return;

    try {
      const headers = await getHeaders();
      const res = await fetch(`${BACKEND_URL}/api/organizations/requests`, {
        method: 'POST',
        headers,
        body: JSON.stringify({
          orgId: reqOrgId,
          name: reqOrgName,
          description: reqOrgDesc
        })
      });

      if (res.ok) {
        setReqOrgId('');
        setReqOrgName('');
        setReqOrgDesc('');
        setSuccessMessage('Workspace creation request submitted! An administrator will review and approve it shortly.');
        setErrorMessage(null);
        fetchPendingRequests();
      } else {
        const err = await res.text();
        setErrorMessage(err);
        setSuccessMessage(null);
      }
    } catch (e) {
      setErrorMessage('Failed to submit workspace request.');
      setSuccessMessage(null);
    }
  };

  const joinWorkspace = async (inviteCode: string) => {
    try {
      const headers = await getHeaders();
      const res = await fetch(`${BACKEND_URL}/api/organizations/join`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ inviteCode })
      });

      if (res.ok) {
        setSuccessMessage('Workspace joined successfully!');
        setErrorMessage(null);
        fetchActiveOrganizations();
        fetchDbProfile();
      } else {
        const err = await res.text();
        setErrorMessage(err);
        setSuccessMessage(null);
      }
    } catch (e) {
      setErrorMessage('Failed to join workspace.');
      setSuccessMessage(null);
    }
  };

  const approveRequest = async (id: number) => {
    try {
      const headers = await getHeaders();
      const res = await fetch(`${BACKEND_URL}/api/organizations/requests/${id}/approve`, {
        method: 'POST',
        headers
      });

      if (res.ok) {
        setSuccessMessage('Workspace request approved successfully!');
        setErrorMessage(null);
        fetchPendingRequests();
        fetchActiveOrganizations();
      } else {
        const err = await res.text();
        setErrorMessage(err);
        setSuccessMessage(null);
      }
    } catch (e) {
      setErrorMessage('Failed to approve workspace request.');
      setSuccessMessage(null);
    }
  };

  const rejectRequest = async (id: number) => {
    try {
      const headers = await getHeaders();
      const res = await fetch(`${BACKEND_URL}/api/organizations/requests/${id}/reject`, {
        method: 'POST',
        headers
      });

      if (res.ok) {
        setSuccessMessage('Workspace request rejected.');
        setErrorMessage(null);
        fetchPendingRequests();
      } else {
        const err = await res.text();
        setErrorMessage(err);
        setSuccessMessage(null);
      }
    } catch (e) {
      setErrorMessage('Failed to reject workspace request.');
      setSuccessMessage(null);
    }
  };

  const vote = async (ideaId: number, type: 'UP' | 'DOWN') => {
    try {
      const headers = await getHeaders();
      const res = await fetch(`${BACKEND_URL}/api/ideas/${ideaId}/vote`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ voteType: type })
      });

      if (res.ok) {
        fetchIdeas();
      } else {
        const err = await res.text();
        setErrorMessage(err);
      }
    } catch (e) {
      setErrorMessage('Failed to submit vote.');
    }
  };

  const fetchComments = async (ideaId: number) => {
    try {
      const headers = await getHeaders();
      const res = await fetch(`${BACKEND_URL}/api/ideas/${ideaId}/comments`, {
        headers
      });
      if (res.ok) {
        const data = await res.json();
        setCommentsByIdea(prev => ({ ...prev, [ideaId]: data }));
      }
    } catch (e) {}
  };

  const toggleComments = (ideaId: number) => {
    const isExpanded = !expandedComments[ideaId];
    setExpandedComments(prev => ({ ...prev, [ideaId]: isExpanded }));
    if (isExpanded) {
      fetchComments(ideaId);
    }
  };

  const submitComment = async (ideaId: number, parentId: number | null = null, isReply: boolean = false) => {
    const content = isReply ? replyInputs[parentId!] : commentInputs[ideaId];
    if (!content || !content.trim()) return;

    try {
      const headers = await getHeaders();
      const res = await fetch(`${BACKEND_URL}/api/ideas/${ideaId}/comments`, {
        method: 'POST',
        headers,
        body: JSON.stringify({
          content: content,
          parentCommentId: parentId
        })
      });

      if (res.ok) {
        if (isReply) {
          setReplyInputs(prev => ({ ...prev, [parentId!]: '' }));
        } else {
          setCommentInputs(prev => ({ ...prev, [ideaId]: '' }));
        }
        fetchComments(ideaId);
      } else {
        const err = await res.text();
        setErrorMessage(err);
      }
    } catch (e) {
      setErrorMessage('Failed to submit comment.');
    }
  };

  const updateStatus = async (ideaId: number, status: string) => {
    try {
      const headers = await getHeaders();
      const res = await fetch(`${BACKEND_URL}/api/ideas/${ideaId}/status`, {
        method: 'PUT',
        headers,
        body: JSON.stringify({ status })
      });

      if (res.ok) {
        setSuccessMessage(`Idea status updated to ${status}`);
        fetchIdeas();
      } else {
        const err = await res.text();
        setErrorMessage(err);
      }
    } catch (e) {
      setErrorMessage('Failed to update status.');
    }
  };


  // Render comments tree
  const CommentNode = ({ comment, allComments }: { comment: Comment; allComments: Comment[] }) => {
    const replies = allComments.filter(c => c.parentCommentId === comment.id);
    const [showReplyForm, setShowReplyForm] = useState(false);

    return (
      <div className="comment-tree-node">
        <div className="comment-box">
          <div className="comment-meta">
            <span className="comment-user">
              <UserIcon size={10} style={{ marginRight: '3px', verticalAlign: 'middle' }} /> 
              {comment.userName || comment.userId}
            </span>
            <span>{new Date(comment.createdAt).toLocaleTimeString()}</span>
          </div>
          <p className="comment-text">{comment.content}</p>
          <button 
            onClick={() => setShowReplyForm(!showReplyForm)}
            className="comment-reply-trigger"
          >
            <CornerDownRight size={10} /> Reply
          </button>
        </div>

        {showReplyForm && (
          <div className="reply-input-row">
            <input 
              type="text"
              placeholder="Write a reply..."
              value={replyInputs[comment.id] || ''}
              onChange={(e) => setReplyInputs(prev => ({ ...prev, [comment.id]: e.target.value }))}
              className="input-field"
            />
            <button 
              onClick={() => {
                submitComment(comment.ideaId, comment.id, true);
                setShowReplyForm(false);
              }}
              className="btn btn-primary"
              style={{ width: 'auto', padding: '0.4rem 1rem', fontSize: '0.75rem' }}
            >
              Reply
            </button>
          </div>
        )}

        {replies.map(reply => (
          <CommentNode key={reply.id} comment={reply} allComments={allComments} />
        ))}
      </div>
    );
  };

  if (!isLoaded) {
    return <div style={{ textAlign: 'center', padding: '5rem', color: 'var(--text-muted)' }}>Loading application context...</div>;
  }

  return (
    <div className="app-container">
      {/* Sign Out State */}
      <SignedOut>
        <div className="welcome-container animate-fade-in" style={{ marginTop: '8rem' }}>
          <div className="welcome-icon-box">
            <Shield size={28} />
          </div>
          <h2 className="welcome-title">Synapse Hub Sign In</h2>
          <p className="welcome-desc">
            Access your secure, multi-tenant workspace. Google Single Sign-On and corporate credentials active.
          </p>
          
          <SignInButton mode="modal">
            <button className="btn btn-primary" style={{ padding: '0.75rem 2rem', fontSize: '0.95rem' }}>
              Sign In to Workspace
            </button>
          </SignInButton>
        </div>
      </SignedOut>

      {/* Signed In State */}
      <SignedIn>
        <header className="header">
          <div className="header-brand">
            <div className="brand-icon">
              <Lightbulb size={20} />
            </div>
            <div>
              <h1 className="brand-title">Synapse Hub</h1>
              <p className="brand-subtitle font-medium">Internal Idea Workspace</p>
            </div>
          </div>

          <div className="header-controls">
            {orgId && (
              <div className="status-info" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginRight: '0.5rem', padding: '0.4rem 0.8rem', borderRadius: '8px', background: 'var(--card-bg)', border: '1px solid var(--card-border)' }}>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Workspace:</span>
                <strong className="status-value" style={{ fontSize: '0.8rem', color: 'var(--text-color)' }}>{currentOrgName}</strong>
                <button 
                  onClick={leaveWorkspace} 
                  className="btn btn-secondary" 
                  style={{ width: 'auto', padding: '0.25rem 0.6rem', fontSize: '0.65rem', marginLeft: '0.25rem', border: '1px solid var(--input-border)' }}
                >
                  Switch Workspace
                </button>
              </div>
            )}
            
            <UserButton afterSignOutUrl="/" />

            <button 
              onClick={toggleTheme}
              className="btn-icon"
              title={theme === 'dark' ? "Toggle Light Mode" : "Toggle Dark Mode"}
            >
              {theme === 'dark' ? <Sun size={16} /> : <Moon size={16} />}
            </button>
          </div>
        </header>

        <main className="main-content">
          
          {/* Notifications */}
          {errorMessage && (
            <div className="banner banner-error animate-fade-in">
              <AlertCircle size={16} style={{ marginTop: '2px' }} />
              <div>{errorMessage}</div>
            </div>
          )}
          {successMessage && (
            <div className="banner banner-success animate-fade-in">
              <Check size={16} style={{ marginTop: '2px' }} />
              <div>{successMessage}</div>
            </div>
          )}
          {!orgId ? (
            /* Scenario A: No Organization Active (Workspace onboarding selection screen) */
            <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
              <div className="welcome-container animate-fade-in" style={{ margin: '2rem auto 0 auto' }}>
                <div className="welcome-icon-box">
                  <Users size={28} />
                </div>
                <h2 className="welcome-title">Welcome to Synapse Hub</h2>
                <p className="welcome-desc">
                  Select an active organization below to join its workspace, or request a new workspace from the administrators.
                </p>
              </div>

              <div className="dashboard-grid">
                {/* Discovery Left column: Join Existing */}
                <div className="card">
                  <h3 className="card-title"><Users size={18} /> Join Active Workspaces</h3>
                  {activeOrgs.length === 0 ? (
                    <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', fontStyle: 'italic' }}>
                      No active workspaces exist yet. Submit a request to initialize the first one!
                    </p>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                      {activeOrgs.map(org => (
                        <div key={org.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'var(--input-bg)', border: '1px solid var(--input-border)', padding: '0.75rem 1rem', borderRadius: '10px', gap: '0.75rem' }}>
                          <div style={{ minWidth: 0, flex: 1 }}>
                            <p style={{ fontWeight: '700', fontSize: '0.9rem', margin: 0, wordBreak: 'break-word' }}>{org.name}</p>
                            <span style={{ fontSize: '0.7rem', color: 'var(--text-muted-more)', wordBreak: 'break-all', display: 'block', marginTop: '0.2rem' }}>ID: {org.id}</span>
                          </div>
                          <button 
                            onClick={() => joinWorkspace(org.inviteCode)}
                            className="btn btn-primary"
                            style={{ width: 'auto', padding: '0.4rem 1rem', fontSize: '0.75rem', flexShrink: 0 }}
                          >
                            Join
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Discovery Right column: Request workspace form */}
                <div className="card">
                  <h3 className="card-title"><Plus size={18} /> Request New Workspace</h3>
                  <form onSubmit={submitWorkspaceRequest}>
                    <div className="form-group">
                      <label className="form-label">Proposed Workspace ID</label>
                      <input 
                        type="text" 
                        placeholder="e.g. org_design_team" 
                        value={reqOrgId} 
                        onChange={(e) => setReqOrgId(e.target.value)}
                        className="input-field"
                        required
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label">Proposed Name</label>
                      <input 
                        type="text" 
                        placeholder="e.g. Design Team Hub" 
                        value={reqOrgName} 
                        onChange={(e) => setReqOrgName(e.target.value)}
                        className="input-field"
                        required
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label">Purpose / Justification</label>
                      <textarea 
                        placeholder="Explain why this workspace is needed" 
                        value={reqOrgDesc} 
                        onChange={(e) => setReqOrgDesc(e.target.value)}
                        className="textarea-field"
                        rows={4}
                      />
                    </div>
                    <button type="submit" className="btn btn-primary" style={{ marginTop: '0.5rem' }}>
                      Submit Workspace Request
                    </button>
                  </form>
                </div>
              </div>

              {/* System Admin Workspace approval queue (bootstrappable if zero orgs, otherwise shows for org admins) */}
              {isSystemAdmin && pendingRequests.length > 0 && (
                <div className="card" style={{ maxWidth: '800px', margin: '0 auto', width: '100%' }}>
                  <h3 className="card-title" style={{ color: 'var(--accent-color)' }}><Shield size={18} /> Pending Workspace Requests</h3>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                    {pendingRequests.map(req => (
                      <div key={req.id} style={{ border: '1px solid var(--input-border)', background: 'var(--input-bg)', borderRadius: '12px', padding: '1rem' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem', marginBottom: '0.75rem' }}>
                          <div>
                            <span style={{ fontSize: '0.7rem', color: 'var(--text-muted-more)', textTransform: 'uppercase', fontWeight: '700' }}>Proposed Org Details</span>
                            <h4 style={{ fontWeight: '700', fontSize: '1rem', margin: '0.15rem 0' }}>{req.name}</h4>
                            <code style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>ID: {req.orgId}</code>
                          </div>
                          <div>
                            <span style={{ fontSize: '0.7rem', color: 'var(--text-muted-more)', textTransform: 'uppercase', fontWeight: '700' }}>Requested By</span>
                            <p style={{ fontSize: '0.85rem', fontWeight: '600' }}>{req.requesterName} ({req.requesterEmail})</p>
                          </div>
                        </div>
                        {req.description && (
                          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '1rem', borderLeft: '3px solid var(--card-border)', paddingLeft: '0.5rem' }}>
                            {req.description}
                          </p>
                        )}
                        <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
                          <button 
                            onClick={() => rejectRequest(req.id)}
                            className="btn btn-secondary"
                            style={{ width: 'auto', padding: '0.4rem 1rem', fontSize: '0.75rem' }}
                          >
                            <XCircle size={12} /> Reject
                          </button>
                          <button 
                            onClick={() => approveRequest(req.id)}
                            className="btn btn-primary"
                            style={{ width: 'auto', padding: '0.4rem 1.25rem', fontSize: '0.75rem' }}
                          >
                            <CheckCircle size={12} /> Approve (Make Admin)
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ) : (
            /* Scenario B: Organization Workspace Active */
            <div className="dashboard-grid animate-fade-in">
              
              {/* Sidebar */}
              <aside className="sidebar">
                
                {/* Org details metadata */}
                <div className="card">
                  <h3 className="card-title" style={{ fontSize: '0.9rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                    Org Metadata
                  </h3>
                  <p style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: '1.25rem' }}>
                    Active workspace secure tenant ID:
                  </p>
                  <div style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', borderRadius: '8px', padding: '0.5rem 0.75rem' }}>
                    <code style={{ fontFamily: 'monospace', fontSize: '0.825rem', fontWeight: '700', color: 'var(--text-color)' }}>
                      {orgId}
                    </code>
                  </div>
                  {activeRole && (
                    <div style={{ marginTop: '1rem', fontSize: '0.75rem', color: 'var(--text-muted)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span>Your Role:</span>
                      <span className={`badge ${activeRole === 'ADMIN' ? 'badge-admin' : 'badge-member'}`}>
                        {activeRole}
                      </span>
                    </div>
                  )}
                </div>

                {/* Submit Idea Card */}
                <div className="card">
                  <h3 className="card-title"><Plus size={16} /> Submit Idea</h3>
                  <form onSubmit={submitIdea}>
                    <div className="form-group">
                      <label className="form-label">Title</label>
                      <input 
                        type="text" 
                        placeholder="Title of your proposal" 
                        value={newTitle} 
                        onChange={(e) => setNewTitle(e.target.value)}
                        maxLength={255}
                        className="input-field"
                        required
                      />
                    </div>

                    <div className="form-group">
                      <label className="form-label">Description</label>
                      <textarea 
                        placeholder="Explain your idea in detail" 
                        value={newDesc} 
                        onChange={(e) => setNewDesc(e.target.value)}
                        maxLength={5000}
                        rows={6}
                        className="textarea-field"
                        required
                      />
                    </div>

                    <div className="form-group">
                      <label className="form-label">Tag</label>
                      <select 
                        value={newTag} 
                        onChange={(e) => setNewTag(e.target.value)}
                        className="select-field"
                      >
                        <option value="Feature">Feature</option>
                        <option value="Bug">Bug</option>
                        <option value="Improvement">Improvement</option>
                        <option value="Design">Design</option>
                        <option value="Other">Other</option>
                      </select>
                    </div>

                    <button type="submit" className="btn btn-primary" style={{ marginTop: '0.5rem' }}>
                      Publish Idea
                    </button>
                  </form>
                </div>

                {/* Workspace requests approval queue (only visible for super admin) */}
                {isSystemAdmin && pendingRequests.length > 0 && (
                  <div className="card">
                    <h3 className="card-title" style={{ fontSize: '0.9rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                      <Shield size={14} /> Workspace Requests
                    </h3>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                      {pendingRequests.map(req => (
                        <div key={req.id} style={{ border: '1px solid var(--input-border)', background: 'var(--input-bg)', borderRadius: '10px', padding: '0.75rem' }}>
                          <p style={{ fontWeight: '700', fontSize: '0.85rem' }}>{req.name}</p>
                          <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Requester: {req.requesterName}</span>
                          <div style={{ display: 'flex', gap: '0.35rem', marginTop: '0.5rem', justifyContent: 'flex-end' }}>
                            <button 
                              onClick={() => rejectRequest(req.id)}
                              className="btn btn-secondary"
                              style={{ width: 'auto', padding: '0.25rem 0.5rem', fontSize: '0.65rem' }}
                            >
                              Reject
                            </button>
                            <button 
                              onClick={() => approveRequest(req.id)}
                              className="btn btn-primary"
                              style={{ width: 'auto', padding: '0.25rem 0.5rem', fontSize: '0.65rem' }}
                            >
                              Approve
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

              </aside>

              {/* Ideas Feed */}
              <section className="feed-section">
                
                {/* Tabs */}
                <div className="feed-header">
                  <h2 className="feed-title">Workspace Ideas</h2>
                  
                  <div className="tabs">
                    <button 
                      onClick={() => setSortBy('hot')}
                      className={`tab-btn ${sortBy === 'hot' ? 'active' : ''}`}
                    >
                      Hot Ranking
                    </button>
                    <button 
                      onClick={() => setSortBy('new')}
                      className={`tab-btn ${sortBy === 'new' ? 'active' : ''}`}
                    >
                      Newest
                    </button>
                    <button 
                      onClick={() => setSortBy('top')}
                      className={`tab-btn ${sortBy === 'top' ? 'active' : ''}`}
                    >
                      Top Upvoted
                    </button>
                  </div>
                </div>

                {/* Feed items */}
                {loading ? (
                  <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}>Loading ideas...</div>
                ) : ideas.length === 0 ? (
                  <div className="card" style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}>
                    No ideas submitted in this workspace yet.
                  </div>
                ) : (
                  ideas.map(idea => (
                    <div key={idea.id} className="idea-card animate-fade-in">
                      
                      {/* Idea meta row */}
                      <div className="idea-card-header">
                        <div className="idea-meta-left">
                          <span className={`tag-badge ${
                            idea.tag === 'Feature' ? 'tag-badge-feature' :
                            idea.tag === 'Bug' ? 'tag-badge-bug' :
                            idea.tag === 'Improvement' ? 'tag-badge-improvement' :
                            idea.tag === 'Design' ? 'tag-badge-design' : 'tag-badge-other'
                          }`}>
                            {idea.tag}
                          </span>

                          <span className={`status-badge ${
                            idea.status === 'SUBMITTED' ? 'status-badge-submitted' :
                            idea.status === 'UNDER_REVIEW' ? 'status-badge-under-review' :
                            idea.status === 'PLANNED' ? 'status-badge-planned' :
                            idea.status === 'IMPLEMENTED' ? 'status-badge-implemented' : 'status-badge-rejected'
                          }`}>
                            {idea.status.replace('_', ' ')}
                          </span>
                        </div>

                        <div className="idea-meta-right">
                          <Clock size={11} />
                          <span>{new Date(idea.createdAt).toLocaleDateString()}</span>
                        </div>
                      </div>

                      {/* Title & description */}
                      <h3 className="idea-title">{idea.title}</h3>
                      <p className="idea-desc">{idea.description}</p>

                      {/* Footer interaction row */}
                      <div className="idea-card-footer">
                        
                        {/* Vote widget */}
                        <div className="vote-widget">
                          <button 
                            onClick={() => vote(idea.id, 'UP')}
                            className="vote-btn"
                          >
                            <ArrowUp size={14} />
                          </button>
                          <span className="vote-count">
                            {idea.upvotesCount - idea.downvotesCount}
                          </span>
                          <button 
                            onClick={() => vote(idea.id, 'DOWN')}
                            className="vote-btn"
                          >
                            <ArrowDown size={14} />
                          </button>
                        </div>

                        {/* Submitter & comments count */}
                        <div className="idea-interactions">
                          <span className="interaction-user">
                            <UserIcon size={11} /> {idea.userName || idea.userId}
                          </span>
                          <button 
                            onClick={() => toggleComments(idea.id)}
                            className="interaction-comments-btn"
                          >
                            <MessageSquare size={12} />
                            <span>Comments</span>
                          </button>
                        </div>

                        {/* Admin status switcher controls */}
                        {activeRole === 'ADMIN' && (
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                            <span style={{ fontSize: '0.65rem', color: 'var(--text-muted-more)', fontWeight: '700', textTransform: 'uppercase' }}>
                              Update Status:
                            </span>
                            <select
                              value={idea.status}
                              onChange={(e) => updateStatus(idea.id, e.target.value)}
                              className="select-field"
                              style={{ width: 'auto', padding: '0.25rem 0.5rem', fontSize: '0.75rem', fontWeight: '700' }}
                            >
                              <option value="SUBMITTED">Submitted</option>
                              <option value="UNDER_REVIEW">Under Review</option>
                              <option value="PLANNED">Planned</option>
                              <option value="IMPLEMENTED">Implemented</option>
                              <option value="REJECTED">Rejected</option>
                            </select>
                          </div>
                        )}

                      </div>

                      {/* Comments section */}
                      {expandedComments[idea.id] && (
                        <div className="comments-container animate-fade-in">
                          <h4 className="comments-header">Threaded Discussion</h4>

                          <div className="comment-input-row">
                            <input 
                              type="text" 
                              placeholder="Add a comment..."
                              value={commentInputs[idea.id] || ''}
                              onChange={(e) => setCommentInputs(prev => ({ ...prev, [idea.id]: e.target.value }))}
                              className="input-field"
                            />
                            <button 
                              onClick={() => submitComment(idea.id)}
                              className="btn btn-primary"
                            >
                              Comment
                            </button>
                          </div>

                          {(!commentsByIdea[idea.id] || commentsByIdea[idea.id].length === 0) ? (
                            <p style={{ fontSize: '0.75rem', color: 'var(--text-muted-more)', fontStyle: 'italic' }}>
                              No comments yet.
                            </p>
                          ) : (
                            commentsByIdea[idea.id]
                              .filter(c => c.parentCommentId === null)
                              .map(comment => (
                                <CommentNode 
                                  key={comment.id} 
                                  comment={comment} 
                                  allComments={commentsByIdea[idea.id]} 
                                />
                              ))
                          )}
                        </div>
                      )}

                    </div>
                  ))
                )}

              </section>
            </div>
          )}
        </main>
      </SignedIn>
    </div>
  );
}

// Responsive layout configuration verified.
// Workspace hooks sorted.
// SignIn spacing adjusted.
// Responsive layout configuration verified.
// Workspace hooks sorted.
// SignIn spacing adjusted.
// Responsive layout configuration verified.
// Workspace hooks sorted.
// SignIn spacing adjusted.