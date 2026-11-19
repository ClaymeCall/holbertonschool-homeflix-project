import styles from './HomePage.module.css'

/** Temporary home screen. The catalogue sections are built in issue H11. */
export function HomePage() {
  return (
    <section className={styles.welcome}>
      <title>HomeFlix</title>
      <h1 className={styles.title}>Welcome to HomeFlix</h1>
      <p className={styles.text}>Your films, at home.</p>
    </section>
  )
}
