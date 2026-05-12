function parseUsages(value) {
  return String(value)
    .split(',')
    .map(v => Number.parseInt(v.trim(), 10))
    .filter(Number.isFinite)
    .filter(v => v >= 0);
}

function TrashIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" width="15" height="15">
      <path d="M9 3h6l1 2h4v2H4V5h4l1-2Zm1 6h2v9h-2V9Zm4 0h2v9h-2V9ZM7 9h10l-.7 11H7.7L7 9Z" fill="currentColor" />
    </svg>
  );
}

function PlusIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" width="15" height="15">
      <path d="M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6V5Z" fill="currentColor" />
    </svg>
  );
}

function makeMacro(index) {
  return {
    label: 'New Macro',
    modifiers: 0,
    keyUsages: [44],
    layoutOffsetX: (index % 2) * 92,
    layoutOffsetY: Math.floor(index / 2) * 54,
    layoutScaleX: 1,
    layoutScaleY: 1,
  };
}

export default function MacroEditor({ config, onConfigChange }) {
  const macros = config?.macroButtons ?? [];

  function updateMacro(index, patch) {
    onConfigChange(prev => {
      const macroButtons = [...(prev?.macroButtons ?? [])];
      macroButtons[index] = { ...(macroButtons[index] ?? makeMacro(index)), ...patch };
      return { ...prev, macroButtons };
    });
  }

  function removeMacro(index) {
    onConfigChange(prev => ({
      ...prev,
      macroButtons: (prev?.macroButtons ?? []).filter((_, i) => i !== index),
    }));
  }

  function addMacro() {
    onConfigChange(prev => {
      const macroButtons = prev?.macroButtons ?? [];
      return { ...prev, macroButtons: [...macroButtons, makeMacro(macroButtons.length)] };
    });
  }

  return (
    <section className="cfg-opt-section">
      <div className="cfg-opt-heading-row">
        <h3 className="cfg-opt-heading">Keyboard Macros</h3>
      </div>

      {macros.length === 0 ? (
        <p className="cfg-opt-hint">No keyboard macros in this config.</p>
      ) : (
        <div className="cfg-macro-list">
          {macros.map((macro, index) => (
            <div key={index} className="cfg-macro-row">
              <button
                type="button"
                className="cfg-icon-btn cfg-macro-trash"
                onClick={() => removeMacro(index)}
                aria-label={`Remove macro ${index + 1}`}
                title="Remove macro"
              >
                <TrashIcon />
              </button>
              <label className="cfg-opt-label cfg-macro-label">
                <span>Macro {index + 1}</span>
                <input
                  className="cfg-opt-input"
                  value={macro.label ?? ''}
                  onChange={e => updateMacro(index, { label: e.target.value })}
                />
              </label>
              <label className="cfg-opt-label cfg-macro-small">
                <span>Mod</span>
                <input
                  className="cfg-opt-input"
                  type="number"
                  min="0"
                  max="255"
                  value={macro.modifiers ?? 0}
                  onChange={e => updateMacro(index, { modifiers: Math.max(0, Math.min(255, Number(e.target.value))) })}
                />
              </label>
              <label className="cfg-opt-label cfg-macro-keys">
                <span>Keys</span>
                <input
                  className="cfg-opt-input"
                  value={(macro.keyUsages ?? []).join(',')}
                  onChange={e => updateMacro(index, { keyUsages: parseUsages(e.target.value) })}
                  placeholder="44"
                />
              </label>
            </div>
          ))}
        </div>
      )}

      <button type="button" className="cfg-add-macro-btn" onClick={addMacro}>
        <PlusIcon />
        <span>Add macro</span>
      </button>
    </section>
  );
}
