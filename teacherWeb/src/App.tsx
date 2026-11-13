import { BrowserRouter, Route, Routes } from 'react-router'
import { backend } from './backend'
import { BackendProvider } from './backend/context'
import { ReportPage } from './pages/ReportPage'

export function App() {
  return (
    <BackendProvider backend={backend}>
      <BrowserRouter>
        <Routes>
          <Route path="/students/:uid/report/:sessionId?" element={<ReportPage />} />
        </Routes>
      </BrowserRouter>
    </BackendProvider>
  )
}
