import { Outlet } from 'react-router'
import { Nav } from './ui/Nav'
import './ui/shell.css'

/** Left navigation + page. */
export function Shell() {
  return (
    <div className="app">
      <Nav />
      <div className="page"><Outlet /></div>
    </div>
  )
}
