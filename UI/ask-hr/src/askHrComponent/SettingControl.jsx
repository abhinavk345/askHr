import React from "react";

const SettingControl = ({
  label,
  enabled,
  value,
  onToggle,
  onChange,
  type = "number",
  placeholder = "Enter value",
  min,
  max,
  step,
}) => {
  return (
    <div className="setting-row">
      <label className="setting-label">
        <input
          type="checkbox"
          checked={enabled}
          onChange={onToggle}
        />
        {label}
      </label>

      <input
        type={type}
        className="setting-input"
        disabled={!enabled}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        min={min}
        max={max}
        step={step}
      />
    </div>
  );
};

export default SettingControl;
