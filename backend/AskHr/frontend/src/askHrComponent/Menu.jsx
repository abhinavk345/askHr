import { useState, useEffect, useRef } from "react";

function Menu({ title, items }) {
  const [open, setOpen] = useState(false);
  const menuRef = useRef(null);

  // Close menu if clicked outside
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleItemClick = (onClick) => {
    onClick();
    setOpen(false); // close menu after clicking an item
  };

  
  return (
    <div className="menu" ref={menuRef}>
      <span className="menu-title" onClick={() => setOpen(!open)}>
        {title}
      </span>
      {open && (
        <div className="menu-dropdown">
          {items.map((item) => (
            <div
              key={item.label}
              className="menu-item"
              onClick={() => handleItemClick(item.onClick)}
            >
              {item.label}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default Menu;
