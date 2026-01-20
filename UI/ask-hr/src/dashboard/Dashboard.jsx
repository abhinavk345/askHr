import React from "react";
import "./Dashboard.css";

const apps = [
  { name: "Appraisal", icon: "📊" },
  { name: "Calendar", icon: "📅" },
  { name: "Discuss", icon: "💬" },
  { name: "ESS", icon: "☝️" },
  { name: "Expenses", icon: "💳" },
  { name: "HelpDesk", icon: "🧑‍💻" },
  { name: "IT Service Desk", icon: "🖥️" },
  { name: "Leaves", icon: "🌴" },
  { name: "Meal Voucher", icon: "🍽️" },
  { name: "Project", icon: "✅" },
  { name: "Referrals", icon: "🤝" },
  { name: "RnR", icon: "🏅" },
  { name: "Room Booking", icon: "🛋️" },
  { name: "Timesheets", icon: "⏱️" },
  { name: "To-do", icon: "✏️" },
  { name: "Travel Desk", icon: "🚌" },
  { name: "Wiki", icon: "📘" }
];

export default function Dashboard() {
  return (
    <div className="dashboard-root">
      {/* Top Navbar */}
      <div className="navbar">
        <div className="navbar-left">
          <span className="menu-icon">☰</span>
          <span className="brand">Discuss</span>
          <span className="config">Configuration</span>
        </div>

        <div className="navbar-right">
          <span className="dot" />
          <span className="bell">🔔</span>
          <span className="company">INTECH Creative Services Pvt Ltd</span>
          <div className="avatar">A</div>
        </div>
      </div>

      {/* Search */}
      <div className="search-container">
        <input type="text" placeholder="Search menus..." />
      </div>

      {/* App Grid */}
      <div className="app-grid">
        {apps.map((app) => (
          <div className="app-tile" key={app.name}>
            <div className="app-icon">{app.icon}</div>
            <div className="app-name">{app.name}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
