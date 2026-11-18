import type { Availability } from '../api/library'
import { availabilityLabel } from '../labels'
import styles from './AvailabilityBadge.module.css'

export function AvailabilityBadge({ availability }: { availability: Availability }) {
  return <span className={`${styles.badge} ${styles[availability]}`}>{availabilityLabel[availability]}</span>
}
