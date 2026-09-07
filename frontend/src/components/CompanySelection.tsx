import React, { useState, useEffect } from 'react';
import { Building2 } from 'lucide-react';

interface Company {
  id: string;
  name: string;
}

interface CompanySelectionProps {
  onCompanySelected: (companyId: string) => Promise<void>;
  backendUrl: string;
  getHeaders: () => Promise<HeadersInit>;
  onRequestNewCompany: () => void;
}

export const CompanySelection: React.FC<CompanySelectionProps> = ({ onCompanySelected, backendUrl, getHeaders, onRequestNewCompany }) => {
  const [companies, setCompanies] = useState<Company[]>([]);
  const [selectedCompanyId, setSelectedCompanyId] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchCompanies();
  }, []);

  const fetchCompanies = async () => {
    try {
      const headers = await getHeaders();
      const res = await fetch(`${backendUrl}/api/companies`, { headers });
      if (res.ok) {
        setCompanies(await res.json());
      } else {
        setError('Failed to load companies.');
      }
    } catch (e) {
      setError('Error connecting to the server.');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedCompanyId) return;
    setLoading(true);
    setError(null);
    try {
      await onCompanySelected(selectedCompanyId);
    } catch (err: any) {
      setError(err.message || 'Failed to select company');
      setLoading(false);
    }
  };

  return (
    <div className="welcome-container animate-fade-in" style={{ margin: '4rem auto 0 auto', maxWidth: '500px' }}>
      <div className="welcome-icon-box">
        <Building2 size={28} />
      </div>
      <h2 className="welcome-title">Select Your Company</h2>
      <p className="welcome-desc" style={{ marginBottom: '2rem' }}>
        To ensure privacy and security, please select the organization you belong to. You will only be able to see and join workspaces within your company.
      </p>

      {error && (
        <div className="banner banner-error animate-fade-in" style={{ marginBottom: '1rem' }}>
          <div>{error}</div>
        </div>
      )}

      <form onSubmit={handleSubmit} className="card" style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        <div className="form-group">
          <label className="form-label">Company</label>
          <select 
            className="input-field" 
            value={selectedCompanyId} 
            onChange={(e) => setSelectedCompanyId(e.target.value)}
            required
            style={{ width: '100%', padding: '0.75rem', borderRadius: '8px', border: '1px solid var(--input-border)', background: 'var(--input-bg)', color: 'var(--text-color)' }}
          >
            <option value="" disabled>-- Select a Company --</option>
            {companies.map(c => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
        </div>
        <button type="submit" className="btn btn-primary" disabled={!selectedCompanyId || loading}>
          {loading ? 'Saving...' : 'Confirm Company'}
        </button>
      </form>
      <div style={{ marginTop: '1.5rem', textAlign: 'center' }}>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '0.5rem' }}>Don't see your company in the list?</p>
        <button 
          onClick={onRequestNewCompany} 
          className="btn btn-secondary" 
          style={{ width: 'auto', padding: '0.5rem 1.5rem', fontSize: '0.9rem' }}
        >
          Request a New Workspace
        </button>
      </div>
    </div>
  );
};
