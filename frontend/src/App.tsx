import { Outlet, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { RequireAdmin, RequireAuth, RequireProfile, RequireSetup, RequireSignedOut } from './auth/guards'
import { AppLayout } from './components/AppLayout'
import { DownloadsPage } from './pages/DownloadsPage'
import { HomePage } from './pages/HomePage'
import { MovieDetailsPage } from './pages/MovieDetailsPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { PlayerPage } from './pages/PlayerPage'
import { ProfilesPage } from './pages/ProfilesPage'
import { SearchPage } from './pages/SearchPage'
import { SetupPage } from './pages/SetupPage'
import { SignInPage } from './pages/SignInPage'

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
          path="profiles"
          element={
            <RequireAuth>
              <ProfilesPage />
            </RequireAuth>
          }
        />
        <Route
          path="watch/:id"
          element={
            <RequireAuth>
              <RequireProfile>
                <PlayerPage />
              </RequireProfile>
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
          {/* The viewing screens show the active profile's list and progress (G10). */}
          <Route
            element={
              <RequireProfile>
                <Outlet />
              </RequireProfile>
            }
          >
            <Route index element={<HomePage />} />
            <Route path="search" element={<SearchPage />} />
            <Route path="movies/:id" element={<MovieDetailsPage />} />
          </Route>
          <Route
            path="downloads"
            element={
              <RequireAdmin>
                <DownloadsPage />
              </RequireAdmin>
            }
          />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </AuthProvider>
  )
}

export default App
