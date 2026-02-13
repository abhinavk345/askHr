import "./SuggestionChips.css"; 
import { useState,useEffect } from "react";

function SuggestionChips({ suggestions = [], onSelect }) {
const [chips, setChips] = useState([]);

useEffect(() => {
  setChips(suggestions);
}, [suggestions]);

  // Function to remove a chip
  const handleRemove = (chipToRemove, e) => {
    e.stopPropagation(); // <-- stop the click from bubbling
    const updated = chips.filter(chip => chip !== chipToRemove);
    setChips(updated);
  };

  // Generate random light color
  const getRandomColor = () => {
    const r = Math.floor(200 + Math.random() * 55); // 200-255
    const g = Math.floor(200 + Math.random() * 55);
    const b = Math.floor(200 + Math.random() * 55);
    return `rgb(${r}, ${g}, ${b})`;
  };

  return (
    <div className="suggestion-chips">
      {chips.map((chip) => (
        <div
          key={chip}
          className="chip"
          style={{ background: getRandomColor() }}
          onClick={() => onSelect(chip)}
        >
          <span>{chip}</span>
          <button 
            className="remove-btn" 
            onClick={(e) => handleRemove(chip, e)}
          >
            ×
          </button>
        </div>
      ))}
    </div>
  );
}

export default SuggestionChips;
