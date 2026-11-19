import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthProvider'
import { RequireAuth, RequireSetup, RequireSignedOut } from './auth/guards'
import { AppLayout } from './components/AppLayout'
import { HomePage } from './pages/HomePage'
import { MovieDetailsPage } from './pages/MovieDetailsPage'
import { NotFoundPage } from './pages/NotFoundPage'
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
          element={
            <RequireAuth>
              <AppLayout />
            </RequireAuth>
          }
        >
          <Route index element={<HomePage />} />
          <Route path="search" element={<SearchPage />} />
          <Route path="movies/:id" element={<MovieDetailsPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </AuthProvider>
  )
}

export default App
