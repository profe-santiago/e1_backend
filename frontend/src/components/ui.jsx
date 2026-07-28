export function ViewHeader({ title, subtitle, children }) {
  return (
    <header className="view-header">
      <div>
        <h2>{title}</h2>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {children && <div className="actions">{children}</div>}
    </header>
  );
}

export function Section({ title, badge, children }) {
  return (
    <section className="section">
      {(title || badge) && (
        <div className="section-head">
          {title ? <h3>{title}</h3> : <span />}
          {badge && <span className="badge info">{badge}</span>}
        </div>
      )}
      <div className="section-body">{children}</div>
    </section>
  );
}

export function Metric({ label, value, variant = "" }) {
  return (
    <article className={`metric ${variant}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

export function Loading({ label }) {
  return <div className="loading">{label}</div>;
}

export function Empty({ label }) {
  return <div className="empty">{label}</div>;
}

export function ErrorBox({ message }) {
  return <div className="error-box">{message}</div>;
}
