import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { ClerkProvider } from '@clerk/clerk-react'
import { MockAuthProvider } from './auth.tsx'

// Load Clerk Publishable Key from Vite environment variables (fallback to default)
const PUBLISHABLE_KEY = import.meta.env.VITE_CLERK_PUBLISHABLE_KEY || 'pk_test_YXJyaXZpbmctY293YmlyZC0yLmNsZXJrLmFjY291bnRzLmRldiQ';
const isRealClerkConfigured = true;

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <MockAuthProvider>
      {isRealClerkConfigured ? (
        <ClerkProvider publishableKey={PUBLISHABLE_KEY} afterSignOutUrl="/">
          <App />
        </ClerkProvider>
      ) : (
        <App />
      )}
    </MockAuthProvider>
  </StrictMode>,
)
