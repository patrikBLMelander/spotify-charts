import React, { useState } from 'react'
import axios from 'axios'
import './ImportForm.css'

const API_BASE_URL = '/api'

function ImportForm({ onImportSuccess }) {
  const [importMode, setImportMode] = useState('json') // 'url' or 'json'
  const [playlistUrl, setPlaylistUrl] = useState('')
  const [jsonData, setJsonData] = useState('')
  const [week, setWeek] = useState('')
  const [weekFromJson, setWeekFromJson] = useState(null) // Week found in JSON
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState(null)
  const [error, setError] = useState(null)

  const getCurrentWeek = () => {
    const now = new Date()
    const startOfYear = new Date(now.getFullYear(), 0, 1)
    const pastDaysOfYear = (now - startOfYear) / 86400000
    const weekNumber = Math.ceil((pastDaysOfYear + startOfYear.getDay() + 1) / 7)
    const year = now.getFullYear()
    return `${year}-W${weekNumber.toString().padStart(2, '0')}`
  }

  const formatAndFixJson = () => {
    if (!jsonData.trim()) {
      setError('Ingen JSON-data att formatera')
      return
    }

    try {
      // Parse JSON
      let parsed = JSON.parse(jsonData)
      const corrections = []
      
      // Check if week exists in JSON
      if (parsed.week) {
        setWeekFromJson(parsed.week)
      } else {
        setWeekFromJson(null)
      }

      // Ensure entries array exists
      if (!parsed.entries) {
        if (Array.isArray(parsed)) {
          // If root is array, wrap it
          parsed = { entries: parsed, week: parsed.week || week || getCurrentWeek() }
          corrections.push('Konverterade root-array till entries')
          setWeekFromJson(parsed.week)
        } else {
          throw new Error('JSON saknar "entries" array')
        }
      }

      // Fix entries array
      if (Array.isArray(parsed.entries)) {
        parsed.entries = parsed.entries.map((entry, index) => {
          const fixed = { ...entry }

          // Fix field names (camelCase -> snake_case)
          if (entry.trackId && !entry.track_id) {
            fixed.track_id = entry.trackId
            delete fixed.trackId
            corrections.push(`Entry ${index + 1}: trackId -> track_id`)
          }
          if (entry.spotifyUrl && !entry.spotify_url) {
            fixed.spotify_url = entry.spotifyUrl
            delete fixed.spotifyUrl
            corrections.push(`Entry ${index + 1}: spotifyUrl -> spotify_url`)
          }

          // Ensure required fields
          if (!fixed.track_id && entry.track_id) {
            fixed.track_id = entry.track_id
          }
          if (!fixed.placement && entry.position) {
            fixed.placement = entry.position
            corrections.push(`Entry ${index + 1}: position -> placement`)
          }
          if (!fixed.placement && entry.rank) {
            fixed.placement = entry.rank
            corrections.push(`Entry ${index + 1}: rank -> placement`)
          }

          // Ensure artists is array
          if (!fixed.artists) {
            fixed.artists = []
          } else if (!Array.isArray(fixed.artists)) {
            fixed.artists = [fixed.artists]
            corrections.push(`Entry ${index + 1}: artists konverterad till array`)
          }

          // Clean up empty or invalid entries
          if (!fixed.track_id || !fixed.title || fixed.title.trim() === '' || fixed.title === '—') {
            return null // Mark for removal
          }

          return fixed
        }).filter(entry => entry !== null) // Remove invalid entries

        if (parsed.entries.length === 0) {
          throw new Error('Inga giltiga entries efter korrigering')
        }
      }

      // Ensure week format
      if (parsed.week && !parsed.week.match(/^\d{4}-W\d{2}$/)) {
        // Try to fix week format
        const weekMatch = parsed.week.match(/(\d{4})[-\s]?[Ww]?(\d{1,2})/)
        if (weekMatch) {
          const year = weekMatch[1]
          const weekNum = weekMatch[2].padStart(2, '0')
          parsed.week = `${year}-W${weekNum}`
          corrections.push(`Vecka korrigerad: ${parsed.week}`)
        } else {
          // Use form week or current week
          parsed.week = week || getCurrentWeek()
          corrections.push(`Vecka satt till: ${parsed.week}`)
        }
      } else if (!parsed.week) {
        parsed.week = week || getCurrentWeek()
        corrections.push(`Vecka tillagd: ${parsed.week}`)
      }

      // Format JSON nicely
      const formatted = JSON.stringify(parsed, null, 2)
      setJsonData(formatted)
      
      if (corrections.length > 0) {
        setMessage(`JSON formaterad och korrigerad:\n${corrections.join('\n')}`)
      } else {
        setMessage('JSON formaterad (inga korrigeringar behövdes)')
      }
      setError(null)
    } catch (err) {
      setError('Kunde inte formatera JSON: ' + err.message)
      setMessage(null)
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setMessage(null)
    setError(null)

    try {
      const weekToUse = week || getCurrentWeek()
      
      if (importMode === 'json') {
        // Parse and validate JSON
        let parsedData
        try {
          parsedData = JSON.parse(jsonData)
        } catch (parseError) {
          throw new Error('Ogiltig JSON: ' + parseError.message)
        }

        // Validate JSON structure
        if (!parsedData.entries || !Array.isArray(parsedData.entries)) {
          throw new Error('JSON måste innehålla "entries" array')
        }

        // Use week from JSON if present, otherwise use form value or current week
        const finalWeek = parsedData.week || weekToUse
        
        if (!finalWeek || !finalWeek.match(/^\d{4}-W\d{2}$/)) {
          throw new Error('Vecka måste anges i formatet YYYY-Www (t.ex. 2026-W05). Ange antingen i JSON eller i fältet nedan.')
        }
        
        const response = await axios.post(`${API_BASE_URL}/import/json`, {
          week: finalWeek,
          entries: parsedData.entries
        })
        setMessage(response.data)
        setJsonData('')
      } else {
        // Spotify URL import
        const response = await axios.post(`${API_BASE_URL}/import`, {
          playlistUrl,
          week: weekToUse
        })
        setMessage(response.data)
        setPlaylistUrl('')
      }
      
      setWeek('')
      if (onImportSuccess) {
        onImportSuccess()
      }
    } catch (err) {
      setError(err.response?.data || err.message || 'Ett fel uppstod vid import')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="import-form-container">
      <h2>Importera ny vecka</h2>
      
      <div className="import-mode-selector">
        <button
          type="button"
          className={`mode-button ${importMode === 'json' ? 'active' : ''}`}
          onClick={() => setImportMode('json')}
        >
          JSON Import
        </button>
        <button
          type="button"
          className={`mode-button ${importMode === 'url' ? 'active' : ''}`}
          onClick={() => setImportMode('url')}
        >
          Spotify URL
        </button>
      </div>

      <form onSubmit={handleSubmit} className="import-form">
        {importMode === 'json' ? (
          <div className="form-group">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
              <label htmlFor="jsonData">JSON Data</label>
              <button
                type="button"
                onClick={formatAndFixJson}
                className="format-button"
                disabled={!jsonData.trim()}
              >
                🔧 Format & Fix JSON
              </button>
            </div>
            <textarea
              id="jsonData"
              value={jsonData}
              onChange={(e) => {
                setJsonData(e.target.value)
                // Try to detect week in JSON
                try {
                  const parsed = JSON.parse(e.target.value)
                  if (parsed.week) {
                    setWeekFromJson(parsed.week)
                  } else {
                    setWeekFromJson(null)
                  }
                } catch {
                  setWeekFromJson(null)
                }
              }}
              placeholder='{"week": "2026-W05", "entries": [...]}'
              rows="15"
              required
              style={{ fontFamily: 'monospace', fontSize: '0.9rem' }}
            />
            <small>Klistra in JSON-data med week och entries. Klicka på "Format & Fix JSON" för att korrigera vanliga fel.</small>
          </div>
        ) : (
          <div className="form-group">
            <label htmlFor="playlistUrl">Spotify Playlist URL</label>
            <input
              type="url"
              id="playlistUrl"
              value={playlistUrl}
              onChange={(e) => setPlaylistUrl(e.target.value)}
              placeholder="https://open.spotify.com/playlist/..."
              required
            />
          </div>
        )}

        <div className="form-group">
          <label htmlFor="week">
            Vecka (YYYY-Www)
            {importMode === 'json' && weekFromJson && (
              <span style={{ color: '#28a745', marginLeft: '0.5rem', fontWeight: 'normal' }}>
                ✓ Hittad i JSON: {weekFromJson}
              </span>
            )}
          </label>
          <input
            type="text"
            id="week"
            value={week}
            onChange={(e) => setWeek(e.target.value)}
            placeholder={importMode === 'json' && weekFromJson ? weekFromJson : getCurrentWeek()}
            pattern="\d{4}-W\d{2}"
            disabled={importMode === 'json' && weekFromJson}
            style={importMode === 'json' && weekFromJson ? { backgroundColor: '#f5f5f5', cursor: 'not-allowed' } : {}}
          />
          <small>
            {importMode === 'json' 
              ? weekFromJson 
                ? 'Vecka hittades i JSON och används automatiskt. Fältet är inaktiverat.'
                : 'Används om week saknas i JSON. Lämna tomt för aktuell vecka: ' + getCurrentWeek()
              : 'Lämna tomt för aktuell vecka: ' + getCurrentWeek()}
          </small>
        </div>

        <button type="submit" disabled={loading} className="submit-button">
          {loading ? 'Importerar...' : 'Importera'}
        </button>
      </form>

      {message && (
        <div className="success-message" style={{ whiteSpace: 'pre-line' }}>
          {message}
        </div>
      )}

      {error && (
        <div className="error-message">
          {typeof error === 'string' ? error : JSON.stringify(error)}
        </div>
      )}
    </div>
  )
}

export default ImportForm
