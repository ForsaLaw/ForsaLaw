import { Award, Loader2 } from 'lucide-react'

const DAY_LABELS = {
  1: 'Lundi',
  2: 'Mardi',
  3: 'Mercredi',
  4: 'Jeudi',
  5: 'Vendredi',
  6: 'Samedi',
  7: 'Dimanche',
}

function asTime(v) {
  return typeof v === 'string' ? v.slice(0, 5) : ''
}

export default function LawyerAgendaSection({
  agendaConfig,
  setAgendaConfig,
  agendaBusy,
  handleSaveAgendaConfig,
  agendaSnapshot,
  handleDeletePlage,
  newPlage,
  setNewPlage,
  handleAddPlage,
  newException,
  setNewException,
  handleDeleteException,
  handleAddException,
  agendaMsg,
  agendaError,
}) {
  return (
    <section className="client-pro-card lawyer-space-section">
      <div className="client-pro-card__head">
        <div className="client-pro-card__icon">
          <Award size={18} />
        </div>
        <div>
          <h2 className="client-pro-card__title">Horaires de travail (agenda)</h2>
          <p className="client-pro-card__sub">Configuration active visible dans votre espace avocat.</p>
        </div>
      </div>
      <div className="client-pro-card__body">
        <form className="client-pro-form" onSubmit={handleSaveAgendaConfig}>
          <div className="client-pro-form__grid">
            <label className="lawyer-space-label">
              <span>Fuseau horaire</span>
              <input
                value={agendaConfig.zoneId}
                onChange={(e) => setAgendaConfig((prev) => ({ ...prev, zoneId: e.target.value }))}
                placeholder="Africa/Tunis"
              />
            </label>
            <label className="lawyer-space-label">
              <span>Duree creneau (min)</span>
              <input
                type="number"
                min={10}
                step={5}
                value={agendaConfig.dureeCreneauMinutes}
                onChange={(e) => setAgendaConfig((prev) => ({ ...prev, dureeCreneauMinutes: e.target.value }))}
              />
            </label>
            <label className="lawyer-space-label">
              <span>Marge entre creneaux (min)</span>
              <input
                type="number"
                min={0}
                step={5}
                value={agendaConfig.bufferMinutes}
                onChange={(e) => setAgendaConfig((prev) => ({ ...prev, bufferMinutes: e.target.value }))}
              />
            </label>
            <label className="lawyer-space-label">
              <span>Agenda actif</span>
              <select
                value={agendaConfig.agendaActif ? 'true' : 'false'}
                onChange={(e) => setAgendaConfig((prev) => ({ ...prev, agendaActif: e.target.value === 'true' }))}
              >
                <option value="true">Oui</option>
                <option value="false">Non</option>
              </select>
            </label>
          </div>
          <button type="submit" className="lawyer-space-btn-primary" disabled={agendaBusy}>
            {agendaBusy ? <Loader2 className="forsalaw-spin" size={18} /> : 'Enregistrer la configuration'}
          </button>
        </form>

        <div className="lawyer-space-readonly-block">
          <p><strong>Plages actuelles</strong></p>
          {(agendaSnapshot?.plages ?? []).length === 0 && <p>Aucune plage configuree.</p>}
          {(agendaSnapshot?.plages ?? []).map((p) => (
            <p key={p.id}>
              {DAY_LABELS[p.dayOfWeek] || `Jour ${p.dayOfWeek}`}: {asTime(p.heureDebut)} - {asTime(p.heureFin)}{' '}
              <button type="button" className="lawyer-space-btn-ghost" onClick={() => handleDeletePlage(p.id)} disabled={agendaBusy}>Supprimer</button>
            </p>
          ))}
        </div>

        <form className="client-pro-form lawyer-space-subform" onSubmit={handleAddPlage}>
          <div className="client-pro-form__grid">
            <label className="lawyer-space-label">
              <span>Jour</span>
              <select
                value={newPlage.dayOfWeek}
                onChange={(e) => setNewPlage((prev) => ({ ...prev, dayOfWeek: Number(e.target.value) }))}
              >
                {Object.entries(DAY_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
            </label>
            <label className="lawyer-space-label">
              <span>Debut</span>
              <input
                type="time"
                value={newPlage.heureDebut}
                onChange={(e) => setNewPlage((prev) => ({ ...prev, heureDebut: e.target.value }))}
                required
              />
            </label>
            <label className="lawyer-space-label">
              <span>Fin</span>
              <input
                type="time"
                value={newPlage.heureFin}
                onChange={(e) => setNewPlage((prev) => ({ ...prev, heureFin: e.target.value }))}
                required
              />
            </label>
          </div>
          <button type="submit" className="lawyer-space-btn-secondary" disabled={agendaBusy}>
            Ajouter une plage
          </button>
        </form>

        <div className="lawyer-space-readonly-block">
          <p><strong>Indisponibilites</strong></p>
          {(agendaSnapshot?.exceptions ?? []).length === 0 && <p>Aucune indisponibilite.</p>}
          {(agendaSnapshot?.exceptions ?? []).map((x) => (
            <p key={x.id}>
              {String(x.dateDebut)} - {String(x.dateFin)} {x.libelle ? `· ${x.libelle}` : ''}{' '}
              <button type="button" className="lawyer-space-btn-ghost" onClick={() => handleDeleteException(x.id)} disabled={agendaBusy}>Supprimer</button>
            </p>
          ))}
        </div>

        <form className="client-pro-form lawyer-space-subform" onSubmit={handleAddException}>
          <div className="client-pro-form__grid">
            <label className="lawyer-space-label">
              <span>Date debut</span>
              <input
                type="date"
                value={newException.dateDebut}
                onChange={(e) => setNewException((prev) => ({ ...prev, dateDebut: e.target.value }))}
                required
              />
            </label>
            <label className="lawyer-space-label">
              <span>Date fin</span>
              <input
                type="date"
                value={newException.dateFin}
                onChange={(e) => setNewException((prev) => ({ ...prev, dateFin: e.target.value }))}
              />
            </label>
            <label className="lawyer-space-label">
              <span>Motif</span>
              <input
                value={newException.libelle}
                onChange={(e) => setNewException((prev) => ({ ...prev, libelle: e.target.value }))}
                placeholder="Conge, audience externe..."
              />
            </label>
          </div>
          <button type="submit" className="lawyer-space-btn-secondary" disabled={agendaBusy}>
            Ajouter une indisponibilite
          </button>
        </form>

        {agendaMsg && <p className="lawyer-space-form-msg">{agendaMsg}</p>}
        {agendaError && <p className="lawyer-space-form-msg lawyer-space-form-msg--err">{agendaError}</p>}
      </div>
    </section>
  )
}
