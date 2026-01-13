import { useState, useRef, useEffect } from "react";

function NavbarRight({ onLogout }) {
  const [open, setOpen] = useState(false);
  const menuRef = useRef(null);

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <div className="navbar-right" ref={menuRef}>
      <span className="dot" />
      <span className="bell">🔔</span>
      <span className="company">INTECH Creative Services Pvt Ltd</span>

      {/* Avatar Button */}
      <button
        className="avatar"
        onClick={() => setOpen((prev) => !prev)}
      >
        A
      </button>

      {/* Dropdown */}
      {open && (
        <div className="avatar-dropdown">
          <button
            className="logout-btn"
            onClick={onLogout}
          >
            Logout
          </button>
        </div>
      )}
    </div>
  );
}

export default NavbarRight;
