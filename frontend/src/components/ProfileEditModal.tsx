import React, { useState } from 'react';
import { X, Upload, Camera } from 'lucide-react';

interface ProfileEditModalProps {
  onClose: () => void;
  onSave: (name: string, profilePicUrl: string) => Promise<void>;
  currentName: string;
  currentProfilePicUrl: string;
  backendUrl: string;
  getHeaders: () => Promise<HeadersInit>;
}

export const ProfileEditModal: React.FC<ProfileEditModalProps> = ({ 
  onClose, onSave, currentName, currentProfilePicUrl, backendUrl, getHeaders 
}) => {
  const [name, setName] = useState(currentName || '');
  const [profilePicUrl, setProfilePicUrl] = useState(currentProfilePicUrl || '');
  const [uploading, setUploading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!e.target.files || e.target.files.length === 0) return;
    const file = e.target.files[0];
    
    if (file.size > 10 * 1024 * 1024) {
      setError('File size must be under 10MB.');
      return;
    }

    setUploading(true);
    setError(null);
    try {
      const formData = new FormData();
      formData.append('file', file);
      
      const headersObj = await getHeaders() as Record<string, string>;
      const tokenHeader = headersObj['Authorization'];

      const res = await fetch(`${backendUrl}/api/upload`, {
        method: 'POST',
        headers: {
          'Authorization': tokenHeader
        },
        body: formData
      });

      if (res.ok) {
        const data = await res.json();
        setProfilePicUrl(data.url);
      } else {
        setError(await res.text());
      }
    } catch (err: any) {
      setError('Upload failed.');
    } finally {
      setUploading(false);
    }
  };

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      await onSave(name, profilePicUrl);
      onClose();
    } catch (err: any) {
      setError(err.message || 'Failed to save profile');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.5)', display: 'flex', justifyContent: 'center', alignItems: 'center', zIndex: 1000 }}>
      <div className="card animate-fade-in" style={{ width: '400px', maxWidth: '90%', position: 'relative' }}>
        <button onClick={onClose} style={{ position: 'absolute', top: '1rem', right: '1rem', background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}>
          <X size={20} />
        </button>
        <h3 className="card-title">Edit Profile</h3>
        
        {error && <div className="banner banner-error" style={{ marginBottom: '1rem', fontSize: '0.8rem' }}>{error}</div>}

        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '1rem', marginBottom: '1.5rem' }}>
          <div style={{ width: '80px', height: '80px', borderRadius: '50%', backgroundColor: 'var(--input-bg)', display: 'flex', justifyContent: 'center', alignItems: 'center', overflow: 'hidden', position: 'relative', border: '2px solid var(--input-border)' }}>
            {profilePicUrl ? (
              <img src={profilePicUrl.startsWith('/') ? backendUrl + profilePicUrl : profilePicUrl} alt="Profile" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            ) : (
              <Camera size={32} color="var(--text-muted)" />
            )}
          </div>
          <label className="btn btn-secondary" style={{ cursor: 'pointer', padding: '0.4rem 1rem', fontSize: '0.75rem' }}>
            {uploading ? 'Uploading...' : <><Upload size={12} style={{ marginRight: '0.5rem', display: 'inline' }}/> Change Picture</>}
            <input type="file" accept="image/*" style={{ display: 'none' }} onChange={handleFileUpload} disabled={uploading} />
          </label>
        </div>

        <div className="form-group">
          <label className="form-label">Name</label>
          <input 
            type="text" 
            className="input-field" 
            value={name} 
            onChange={(e) => setName(e.target.value)} 
            placeholder="Your full name"
          />
        </div>

        <button onClick={handleSave} className="btn btn-primary" style={{ width: '100%' }} disabled={saving || uploading}>
          {saving ? 'Saving...' : 'Save Profile'}
        </button>
      </div>
    </div>
  );
};
