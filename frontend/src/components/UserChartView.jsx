import React, { useState, useEffect } from 'react'
import axios from 'axios'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import './UserChartView.css'

const API_BASE_URL = 'http://localhost:8080/api'

function UserChartView({ user, onWeekChange, onTracksLoaded }) {
  const [weeks, setWeeks] = useState([])
  const [selectedWeek, setSelectedWeek] = useState('')
  const [chartEntries, setChartEntries] = useState([])
  const [droppedTracks, setDroppedTracks] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [selectedTracks, setSelectedTracks] = useState(new Set())
  const [trackHistories, setTrackHistories] = useState({})
  const [visibleTracks, setVisibleTracks] = useState(new Set())
  const [showImportModal, setShowImportModal] = useState(false)
  const [importJson, setImportJson] = useState('')
  const [importing, setImporting] = useState(false)
  const [importError, setImportError] = useState(null)
  const [importSuccess, setImportSuccess] = useState(false)

  useEffect(() => {
    loadWeeks()
  }, [user])

  useEffect(() => {
    if (selectedWeek) {
      loadTracksForWeek(selectedWeek)
    }
  }, [selectedWeek, user])

  useEffect(() => {
    if (selectedWeek && onWeekChange) {
      onWeekChange(selectedWeek)
    }
  }, [selectedWeek])

  const loadWeeks = async () => {
    try {
      setLoading(true)
      const response = await axios.get(`${API_BASE_URL}/weeks/${user}`)
      const weekList = response.data.sort().reverse() // Most recent first
      setWeeks(weekList)
      if (weekList.length > 0 && !selectedWeek) {
        const firstWeek = weekList[0]
        setSelectedWeek(firstWeek) // Select most recent week
        if (onWeekChange) {
          onWeekChange(firstWeek)
        }
      }
      setError(null)
    } catch (err) {
      setError('Kunde inte ladda veckor: ' + (err.response?.data?.message || err.message))
    } finally {
      setLoading(false)
    }
  }

  const loadTracksForWeek = async (week) => {
    try {
      setLoading(true)
      const [chartResponse, droppedResponse] = await Promise.all([
        axios.get(`${API_BASE_URL}/chart/${user}?week=${week}`),
        axios.get(`${API_BASE_URL}/chart/${user}/dropped?week=${week}`)
      ])
      setChartEntries(chartResponse.data)
      setDroppedTracks(droppedResponse.data)
      setError(null)
    } catch (err) {
      setError('Kunde inte ladda låtar: ' + (err.response?.data?.message || err.message))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (chartEntries.length > 0 && onTracksLoaded) {
      onTracksLoaded(chartEntries)
    }
  }, [chartEntries])

  useEffect(() => {
    loadTrackHistories()
  }, [selectedTracks, user])

  const loadTrackHistories = async () => {
    if (selectedTracks.size === 0) {
      setTrackHistories({})
      return
    }

    const histories = {}
    for (const trackId of selectedTracks) {
      try {
        const response = await axios.get(`${API_BASE_URL}/tracks/${trackId}/history?user=${user}`)
        histories[trackId] = response.data
      } catch (err) {
        console.error(`Error loading history for track ${trackId}:`, err)
      }
    }
    setTrackHistories(histories)
  }

  const toggleTrackSelection = (trackId) => {
    const newSelected = new Set(selectedTracks)
    if (newSelected.has(trackId)) {
      newSelected.delete(trackId)
    } else {
      newSelected.add(trackId)
    }
    setSelectedTracks(newSelected)
  }

  const prepareChartData = () => {
    if (selectedTracks.size === 0) return []

    // Sort weeks in ascending order (oldest first) for chart display
    // This matches the visual expectation: left = older, right = newer
    const allWeeks = [...weeks].sort()

    // Create data points for each week
    return allWeeks.map(week => {
      const dataPoint = { week }
      Object.entries(trackHistories).forEach(([trackId, history]) => {
        const point = history.history.find(p => p.week === week)
        const trackName = `${history.track.title} - ${history.track.artists.join(', ')}`
        // Only include position if track was in that week, otherwise undefined (not null)
        dataPoint[trackName] = point ? point.position : undefined
      })
      return dataPoint
    })
  }

  const getTrackColor = (index) => {
    const colors = [
      '#1db954', '#ff6b6b', '#4ecdc4', '#45b7d1', 
      '#f9ca24', '#f0932b', '#eb4d4b', '#6c5ce7',
      '#a29bfe', '#fd79a8', '#00b894', '#00cec9'
    ]
    return colors[index % colors.length]
  }
  
  const getCurrentWeekIndex = () => {
    const index = weeks.indexOf(selectedWeek)
    return index === -1 ? 0 : index
  }

  const goToPreviousWeek = () => {
    const currentIndex = getCurrentWeekIndex()
    // Go to newer week (lower index in reversed array)
    if (currentIndex > 0 && !loading) {
      const newWeek = weeks[currentIndex - 1]
      setSelectedWeek(newWeek)
    }
  }

  const goToNextWeek = () => {
    const currentIndex = getCurrentWeekIndex()
    // Go to older week (higher index in reversed array)
    if (currentIndex < weeks.length - 1 && !loading) {
      const newWeek = weeks[currentIndex + 1]
      setSelectedWeek(newWeek)
    }
  }

  const canGoPrevious = () => {
    // Can go to newer week (lower index)
    if (loading || weeks.length === 0) return false
    const currentIndex = getCurrentWeekIndex()
    return currentIndex > 0
  }

  const canGoNext = () => {
    // Can go to older week (higher index)
    if (loading || weeks.length === 0) return false
    const currentIndex = getCurrentWeekIndex()
    return currentIndex < weeks.length - 1
  }

  const getStatusIcon = (entry) => {
    if (entry.previousPosition === null || entry.previousPosition === undefined) {
      // New entry
      return <span className="status-icon status-new">●</span>
    }
    
    const diff = entry.previousPosition - entry.position
    if (diff > 0) {
      // Moved up
      return <span className="status-icon status-up">↑ {diff}</span>
    } else if (diff < 0) {
      // Moved down
      return <span className="status-icon status-down">↓ {Math.abs(diff)}</span>
    } else {
      // Same position
      return <span className="status-icon status-same">—</span>
    }
  }

  // Import handler function
  const handleImport = async () => {
    try {
      setImporting(true)
      setImportError(null)
      setImportSuccess(false)

      // Parse JSON to validate
      const jsonData = JSON.parse(importJson)
      
      // Import via API
      const response = await axios.post(
        `${API_BASE_URL}/import/json?user=${user}`,
        jsonData
      )

      setImportSuccess(true)
      
      // Reload weeks and current week if imported week matches
      await loadWeeks()
      if (jsonData.week === selectedWeek || !selectedWeek) {
        if (jsonData.week) {
          setSelectedWeek(jsonData.week)
          await loadTracksForWeek(jsonData.week)
        }
      }

      // Clear and close after 2 seconds
      setTimeout(() => {
        setShowImportModal(false)
        setImportJson('')
        setImportError(null)
        setImportSuccess(false)
      }, 2000)
    } catch (err) {
      console.error('Import error:', err)
      if (err.response?.data) {
        // Backend returned an error response
        const errorData = err.response.data
        if (typeof errorData === 'string') {
          setImportError(errorData)
        } else if (errorData.message) {
          setImportError(errorData.message)
        } else if (errorData.error) {
          setImportError(errorData.error)
        } else {
          setImportError('Fel vid import: ' + JSON.stringify(errorData))
        }
      } else if (err.message && err.message.includes('JSON')) {
        setImportError('Ogiltig JSON-format. Kontrollera att JSON:en är korrekt formaterad.')
      } else {
        setImportError('Fel vid import: ' + (err.message || 'Okänt fel'))
      }
    } finally {
      setImporting(false)
    }
  }

  return (
    <div className="user-chart-view">

      {error && (
        <div className="error-message">
          {error}
        </div>
      )}

      <div className="week-selector">
        <div className="week-navigation">
          <button
            className="week-nav-button"
            onClick={(e) => {
              e.preventDefault()
              e.stopPropagation()
              goToNextWeek()
            }}
            disabled={!canGoNext()}
            aria-label="Föregående vecka"
            type="button"
          >
            ←
          </button>
          <span className="week-display" key={selectedWeek}>
            {selectedWeek || 'Ingen vecka vald'}
          </span>
          <button
            className="week-nav-button"
            onClick={(e) => {
              e.preventDefault()
              e.stopPropagation()
              goToPreviousWeek()
            }}
            disabled={!canGoPrevious()}
            aria-label="Nästa vecka"
            type="button"
          >
            →
          </button>
        </div>
      </div>

      {loading && !selectedWeek ? (
        <div className="loading">Laddar...</div>
      ) : selectedWeek ? (
        <>
          <div className="chart-table-container">
            <table className="chart-table">
              <thead>
                    <tr>
                      <th>Placering</th>
                      <th>Status</th>
                      <th>Artist</th>
                      <th>Låttitel</th>
                      <th>Länk</th>
                    </tr>
              </thead>
              <tbody>
                {chartEntries.map(entry => (
                  <tr 
                    key={entry.track.id}
                    className={selectedTracks.has(entry.track.id) ? 'selected-row' : ''}
                    onClick={() => toggleTrackSelection(entry.track.id)}
                    style={{ cursor: 'pointer' }}
                  >
                    <td className="position-cell">{entry.position}</td>
                    <td className="status-cell">{getStatusIcon(entry)}</td>
                    <td className="artist-cell">{entry.track.artists.join(', ')}</td>
                    <td className="title-cell">{entry.track.title}</td>
                    <td className="link-cell">
                      <a 
                        href={entry.track.spotifyUrl} 
                        target="_blank" 
                        rel="noopener noreferrer"
                        className="spotify-thumbnail-link"
                        title={`Öppna ${entry.track.title} på Spotify`}
                        onClick={(e) => e.stopPropagation()}
                      >
                        {entry.track.imageUrl ? (
                          <img 
                            src={entry.track.imageUrl} 
                            alt={`${entry.track.title} by ${entry.track.artists.join(', ')}`}
                            className="track-thumbnail"
                            onError={(e) => {
                              // Fallback to text if image fails to load
                              e.target.style.display = 'none';
                              if (e.target.nextSibling) {
                                e.target.nextSibling.style.display = 'inline';
                              }
                            }}
                          />
                        ) : (
                          <span className="spotify-link-fallback">🎵 Spotify</span>
                        )}
                      </a>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {selectedTracks.size > 0 && (
            <div className="chart-diagram-section">
              <h3 className="chart-diagram-header">
                📈 Veckoprogress för valda låtar
              </h3>
              <div className="chart-diagram-container">
                <ResponsiveContainer width="100%" height={400} className="chart-responsive-container">
                  <LineChart 
                    data={prepareChartData()}
                    margin={{ top: 10, right: 10, left: 0, bottom: 100 }}
                  >
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border-color)" />
                    <XAxis 
                      dataKey="week" 
                      stroke="var(--text-secondary)"
                      style={{ fontSize: '0.85rem' }}
                      angle={-45}
                      textAnchor="end"
                      height={60}
                      interval={0}
                    />
                    <YAxis 
                      reversed
                      domain={[1, 50]}
                      stroke="var(--text-secondary)"
                      style={{ fontSize: '0.85rem' }}
                      width={35}
                      tickCount={6}
                    />
                    <Tooltip 
                      contentStyle={{ 
                        backgroundColor: 'var(--bg-secondary)', 
                        border: '1px solid var(--border-color)',
                        color: 'var(--text-primary)',
                        borderRadius: '8px',
                        padding: '0.75rem',
                        fontSize: '0.85rem'
                      }}
                      labelStyle={{ 
                        color: 'var(--text-primary)',
                        fontWeight: 600,
                        marginBottom: '0.5rem',
                        fontSize: '0.9rem'
                      }}
                      formatter={(value, name) => {
                        if (value === null || value === undefined) return null
                        return [`Placering: ${value}`, name]
                      }}
                    />
                    <Legend 
                      wrapperStyle={{ display: 'none' }}
                    />
                    {Object.entries(trackHistories).map(([trackId, history], index) => {
                      const trackName = `${history.track.title} - ${history.track.artists.join(', ')}`
                      const isVisible = visibleTracks.has(trackId)
                      const displayName = isVisible 
                        ? `${history.track.title} - ${history.track.artists.join(', ')}`
                        : history.track.title
                      return (
                        <Line
                          key={trackId}
                          type="monotone"
                          dataKey={trackName}
                          stroke={getTrackColor(index)}
                          strokeWidth={2.5}
                          dot={{ r: 5, fill: getTrackColor(index) }}
                          activeDot={{ r: 7, stroke: getTrackColor(index), strokeWidth: 2 }}
                          connectNulls={false}
                          name={displayName}
                        />
                      )
                    })}
                  </LineChart>
                </ResponsiveContainer>
                <div className="custom-legend">
                  {Object.entries(trackHistories).map(([trackId, history], index) => {
                    const isVisible = visibleTracks.has(trackId)
                    return (
                      <div 
                        key={trackId}
                        className="legend-item"
                        onClick={() => {
                          const newVisible = new Set(visibleTracks)
                          if (newVisible.has(trackId)) {
                            newVisible.delete(trackId)
                          } else {
                            newVisible.add(trackId)
                          }
                          setVisibleTracks(newVisible)
                        }}
                        style={{ cursor: 'pointer' }}
                      >
                        <span 
                          className="legend-color"
                          style={{ backgroundColor: getTrackColor(index) }}
                        />
                        <span className="legend-name">
                          {isVisible 
                            ? `${history.track.title} - ${history.track.artists.join(', ')}`
                            : history.track.title
                          }
                        </span>
                      </div>
                    )
                  })}
                </div>
              </div>
            </div>
          )}

          {droppedTracks.length > 0 && (
            <div className="dropped-tracks-section">
              <h3 className="dropped-tracks-header">
                🚫 Låtar som åkt ur listan (var med förra veckan)
              </h3>
              <div className="chart-table-container">
                <table className="chart-table dropped-table">
                  <thead>
                    <tr>
                      <th>Föregående placering</th>
                      <th>Artist</th>
                      <th>Låttitel</th>
                      <th>Länk</th>
                    </tr>
                  </thead>
                  <tbody>
                    {droppedTracks.map(entry => (
                      <tr key={entry.track.id} className="dropped-row">
                        <td className="position-cell">{entry.position}</td>
                        <td className="artist-cell">{entry.track.artists.join(', ')}</td>
                        <td className="title-cell">{entry.track.title}</td>
                        <td className="link-cell">
                          <a 
                            href={entry.track.spotifyUrl} 
                            target="_blank" 
                            rel="noopener noreferrer"
                            className="spotify-thumbnail-link"
                            title={`Öppna ${entry.track.title} på Spotify`}
                          >
                            {entry.track.imageUrl ? (
                              <img 
                                src={entry.track.imageUrl} 
                                alt={`${entry.track.title} by ${entry.track.artists.join(', ')}`}
                                className="track-thumbnail"
                                onError={(e) => {
                                  e.target.style.display = 'none';
                                  if (e.target.nextSibling) {
                                    e.target.nextSibling.style.display = 'inline';
                                  }
                                }}
                              />
                            ) : (
                              <span className="spotify-link-fallback">🎵 Spotify</span>
                            )}
                          </a>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      ) : (
        <div className="no-data">
          <p>Ingen data tillgänglig. Lägg JSON-filer i mappen <code>data/{user}/</code>.</p>
        </div>
      )}

      {/* Import Button */}
      <div className="import-section">
        <button 
          className="import-button"
          onClick={() => setShowImportModal(true)}
        >
          📥 Importera JSON-data
        </button>
      </div>

      {/* Import Modal */}
      {showImportModal && (
        <div className="modal-overlay" onClick={() => setShowImportModal(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Importera JSON-data</h2>
              <button 
                className="modal-close"
                onClick={() => {
                  setShowImportModal(false)
                  setImportJson('')
                  setImportError(null)
                  setImportSuccess(false)
                }}
              >
                ×
              </button>
            </div>
            <div className="modal-body">
              <p>Klistra in JSON-data med samma struktur som befintliga filer:</p>
              <textarea
                className="import-textarea"
                value={importJson}
                onChange={(e) => setImportJson(e.target.value)}
                placeholder={`{\n  "week": "2026-W06",\n  "entries": [\n    {\n      "placement": 1,\n      "track_id": "...",\n      "title": "...",\n      "artists": ["..."],\n      "spotify_url": "...",\n      "image_url": "..."\n    }\n  ]\n}`}
                rows={15}
              />
              {importError && (
                <div className="import-error">{importError}</div>
              )}
              {importSuccess && (
                <div className="import-success">Data importerad framgångsrikt!</div>
              )}
            </div>
            <div className="modal-footer">
              <button
                className="import-submit-button"
                onClick={handleImport}
                disabled={importing || !importJson.trim()}
              >
                {importing ? 'Importerar...' : 'Importera'}
              </button>
              <button
                className="import-cancel-button"
                onClick={() => {
                  setShowImportModal(false)
                  setImportJson('')
                  setImportError(null)
                  setImportSuccess(false)
                }}
              >
                Avbryt
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default UserChartView
