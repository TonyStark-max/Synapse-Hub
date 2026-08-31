import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { ClerkProvider } from '@clerk/clerk-react'
import { MockAuthProvider } from './auth.tsx'

// Load Clerk Publishable Key from Vite environment variables
const PUBLISHABLE_KEY = import.meta.env.VITE_CLERK_PUBLISHABLE_KEY;
const isRealClerkConfigured = PUBLISHABLE_KEY && !PUBLISHABLE_KEY.startsWith('pk_test_dummy');

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
