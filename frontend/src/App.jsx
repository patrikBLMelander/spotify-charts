import React, { useState, useEffect, useCallback } from 'react'
import UserChartView from './components/UserChartView'
import './App.css'

function App() {
  const [activeUser, setActiveUser] = useState('Walter')
  const [selectedWeek, setSelectedWeek] = useState('')
  const [topTracks, setTopTracks] = useState([])
  const [deferredPrompt, setDeferredPrompt] = useState(null)
  const [isInstallable, setIsInstallable] = useState(false)

  const handleWeekChange = useCallback((week) => {
    setSelectedWeek(week)
  }, [])

  const handleTracksLoaded = useCallback((tracks) => {
    setTopTracks(tracks)
  }, [])

  useEffect(() => {
    const handler = (e) => {
      // Prevent the mini-infobar from appearing on mobile
      e.preventDefault()
      // Stash the event so it can be triggered later
      setDeferredPrompt(e)
      setIsInstallable(true)
    }

    window.addEventListener('beforeinstallprompt', handler)

    // Check if app is already installed
    if (window.matchMedia('(display-mode: standalone)').matches) {
      setIsInstallable(false)
    }

    return () => {
      window.removeEventListener('beforeinstallprompt', handler)
    }
  }, [])

  const handleInstall = async () => {
    if (!deferredPrompt) {
      // If deferredPrompt is not available, show a message
      alert('Appen kan installeras via webbläsarens meny (t.ex. "Lägg till på hemskärmen")')
      return
    }

    // Show the install prompt
    deferredPrompt.prompt()

    // Wait for the user to respond to the prompt
    const { outcome } = await deferredPrompt.userChoice

    if (outcome === 'accepted') {
      console.log('Användaren accepterade install-prompten')
    } else {
      console.log('Användaren avvisade install-prompten')
    }

    // Clear the deferredPrompt
    setDeferredPrompt(null)
    setIsInstallable(false)
  }

  useEffect(() => {
    // Always use dark mode
    document.documentElement.classList.add('dark-mode')
  }, [])

  const topFourTracks = topTracks.slice(0, 4)

  return (
    <div className="app dark-mode">
      <header className="app-header">
        <div className="header-content">
          <div className="header-left">
            {topFourTracks.length > 0 && (
              <div className="header-album-covers">
                {topFourTracks.map((entry, index) => (
                  <div key={entry.track.id} className="album-cover-wrapper" style={{ zIndex: 4 - index }}>
                    {entry.track.imageUrl ? (
                      <img 
                        src={entry.track.imageUrl} 
                        alt={`${entry.track.title}`}
                        className="album-cover"
                      />
                    ) : (
                      <div className="album-cover-placeholder">🎵</div>
                    )}
                  </div>
                ))}
              </div>
            )}
            <div className="header-text">
              <h1>{activeUser}</h1>
              <p>Top 50 Charts</p>
            </div>
          </div>
        </div>
      </header>

      <main className="app-main">
        <div className="user-selector">
          <button
            className={`user-button ${activeUser === 'Walter' ? 'active' : ''}`}
            onClick={() => {
              setActiveUser('Walter')
              setSelectedWeek('')
            }}
          >
            Walter
          </button>
          <button
            className={`user-button ${activeUser === 'Signe' ? 'active' : ''}`}
            onClick={() => {
              setActiveUser('Signe')
              setSelectedWeek('')
            }}
          >
            Signe
          </button>
        </div>

        <div className="app-content">
          <UserChartView 
            user={activeUser} 
            onWeekChange={handleWeekChange}
            onTracksLoaded={handleTracksLoaded}
            key={activeUser}
          />
        </div>
      </main>

      <footer className="app-footer">
        <div className="footer-content">
          <button 
            onClick={handleInstall} 
            className="footer-download-button" 
            disabled={!isInstallable}
            title={isInstallable ? 'Installera appen' : 'Appen är redan installerad eller kan inte installeras'}
          >
            {isInstallable ? '⬇️ Installera app' : '✓ Installerad'}
          </button>
        </div>
      </footer>
    </div>
  )
}

export default App
