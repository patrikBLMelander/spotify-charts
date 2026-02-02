import React, { useState, useEffect } from 'react'
import axios from 'axios'
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer
} from 'recharts'
import './ChartView.css'

const API_BASE_URL = 'http://localhost:8080/api'

// Generate colors for tracks
const COLORS = [
  '#1db954', '#ff6b6b', '#4ecdc4', '#45b7d1', '#f9ca24',
  '#f0932b', '#eb4d4b', '#6c5ce7', '#a29bfe', '#fd79a8',
  '#00b894', '#00cec9', '#55efc4', '#74b9ff', '#0984e3'
]

function ChartView({ trackIds }) {
  const [chartData, setChartData] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (trackIds.length === 0) {
      setChartData([])
      return
    }
    loadChartData()
  }, [trackIds])

  const loadChartData = async () => {
    setLoading(true)
    setError(null)

    try {
      const histories = await Promise.all(
        trackIds.map(trackId =>
          axios.get(`${API_BASE_URL}/tracks/${trackId}/history`)
        )
      )

      // Transform data for Recharts
      // Group by week and create data points
      const weekMap = new Map()

      histories.forEach((response, index) => {
        const trackHistory = response.data
        const track = trackHistory.track
        const color = COLORS[index % COLORS.length]

        trackHistory.history.forEach(point => {
          if (!weekMap.has(point.week)) {
            weekMap.set(point.week, { week: point.week })
          }
          const dataPoint = weekMap.get(point.week)
          // Invert position for better visualization (1 = top, 50 = bottom)
          dataPoint[track.id] = point.position
          dataPoint[`${track.id}_color`] = color
          dataPoint[`${track.id}_label`] = `${track.title} - ${track.artists[0]}`
        })
      })

      const sortedData = Array.from(weekMap.values())
        .sort((a, b) => a.week.localeCompare(b.week))

      setChartData(sortedData)
    } catch (err) {
      setError('Kunde inte ladda diagramdata: ' + (err.response?.data?.message || err.message))
    } finally {
      setLoading(false)
    }
  }

  if (loading) {
    return <div className="chart-loading">Laddar diagram...</div>
  }

  if (error) {
    return <div className="chart-error">{error}</div>
  }

  if (chartData.length === 0) {
    return (
      <div className="chart-container">
        <p className="chart-empty">Ingen data tillgänglig för valda låtar</p>
      </div>
    )
  }

  // Get unique track labels for legend
  const trackLabels = trackIds.map((trackId, index) => {
    const firstDataPoint = chartData.find(d => d[trackId] !== undefined)
    return {
      trackId,
      label: firstDataPoint?.[`${trackId}_label`] || `Låt ${index + 1}`,
      color: COLORS[index % COLORS.length]
    }
  })

  return (
    <div className="chart-container">
      <h2>Placeringshistorik</h2>
      <div className="chart-wrapper">
        <ResponsiveContainer width="100%" height={500}>
          <LineChart data={chartData} margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis
              dataKey="week"
              angle={-45}
              textAnchor="end"
              height={100}
              interval={0}
            />
            <YAxis
              domain={[1, 50]}
              reversed
              label={{ value: 'Placering', angle: -90, position: 'insideLeft' }}
            />
            <Tooltip
              formatter={(value, name) => {
                if (value === undefined) return null
                const trackLabel = trackLabels.find(t => t.trackId === name)?.label || name
                return [`Placering ${value}`, trackLabel]
              }}
            />
            <Legend
              formatter={(value) => {
                const trackLabel = trackLabels.find(t => t.trackId === value)?.label || value
                return trackLabel
              }}
            />
            {trackIds.map((trackId, index) => (
              <Line
                key={trackId}
                type="monotone"
                dataKey={trackId}
                stroke={COLORS[index % COLORS.length]}
                strokeWidth={2}
                dot={{ r: 4 }}
                activeDot={{ r: 6 }}
                name={trackId}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

export default ChartView
