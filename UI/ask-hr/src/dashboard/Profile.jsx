import React from "react"; 
import Dashboard from "./Dashboard";
import AskAI from "../askHrComponent/AskAI";

function Profile({ user }) {
  return (
    <div className="profile-container">
      <Dashboard />
      <AskAI user={user} />
    </div>
  );
}

export default Profile;