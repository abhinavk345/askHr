import "./SuggestionChips.css";

function SuggestionChips({ suggestions = [], onSelect }) {
  return (
    <div className="suggestion-chips">
      {suggestions.map((chip) => (
        <button
          key={chip}
          className="chip"
          onClick={() => onSelect(chip)}
        >
          {chip}
        </button>
      ))}
    </div>
  );
}

export default SuggestionChips;
