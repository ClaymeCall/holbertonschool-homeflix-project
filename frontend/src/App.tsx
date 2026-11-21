import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { RequireAdmin, RequireAuth, RequireSetup, RequireSignedOut } from './auth/guards'
import { AdminLayout } from './components/AdminLayout'
import { AppLayout } from './components/AppLayout'
import { AccountsPage } from './pages/AccountsPage'
import { DownloadsPage } from './pages/DownloadsPage'
import { HomePage } from './pages/HomePage'
import { LibraryPage } from './pages/LibraryPage'
import { MediaEditPage } from './pages/MediaEditPage'
import { MovieDetailsPage } from './pages/MovieDetailsPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { PlayerPage } from './pages/PlayerPage'
import { SearchPage } from './pages/SearchPage'
import { SetupPage } from './pages/SetupPage'
import { SignInPage } from './pages/SignInPage'
import { StatusPage } from './pages/StatusPage'

function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route
          path="setup"
          element={
            <RequireSetup>
              <SetupPage />
            </RequireSetup>
          }
        />
        <Route
          path="sign-in"
          element={
            <RequireSignedOut>
              <SignInPage />
            </RequireSignedOut>
          }
        />
        <Route
          path="watch/:id"
          element={
            <RequireAuth>
              <PlayerPage />
            </RequireAuth>
          }
        />
        <Route
          element={
            <RequireAuth>
              <AppLayout />
            </RequireAuth>
          }
        >
          <Route index element={<HomePage />} />
          <Route path="search" element={<SearchPage />} />
          <Route path="movies/:id" element={<MovieDetailsPage />} />
          <Route
            path="admin"
            element={
              <RequireAdmin>
                <AdminLayout />
              </RequireAdmin>
            }
          >
            <Route index element={<Navigate to="status" replace />} />
            <Route path="accounts" element={<AccountsPage />} />
            <Route path="library" element={<LibraryPage />} />
            <Route path="library/media/:id" element={<MediaEditPage />} />
            <Route path="downloads" element={<DownloadsPage />} />
            <Route path="status" element={<StatusPage />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </AuthProvider>
  )
}

export default App
