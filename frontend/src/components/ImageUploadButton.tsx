import React, { useState } from 'react';
import { Image as ImageIcon } from 'lucide-react';

interface ImageUploadButtonProps {
  onUploadSuccess: (url: string) => void;
  backendUrl: string;
  getHeaders: () => Promise<HeadersInit>;
}

export const ImageUploadButton: React.FC<ImageUploadButtonProps> = ({ onUploadSuccess, backendUrl, getHeaders }) => {
  const [uploading, setUploading] = useState(false);
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
        onUploadSuccess(data.url);
      } else {
        setError(await res.text());
      }
    } catch (err: any) {
      setError('Upload failed.');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem' }}>
      <label className="btn-icon" style={{ cursor: 'pointer', opacity: uploading ? 0.5 : 1, display: 'inline-flex', padding: '0.5rem' }}>
        <ImageIcon size={16} />
        <input type="file" accept="image/*" style={{ display: 'none' }} onChange={handleFileUpload} disabled={uploading} />
      </label>
      {error && <span style={{ color: 'var(--accent-color)', fontSize: '0.7rem' }}>{error}</span>}
      {uploading && <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Uploading...</span>}
    </div>
  );
};
