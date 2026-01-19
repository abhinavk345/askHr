import { useState } from "react";

const LoginCard = ({ updateIsLogin }) => {
  const [employeeId, setEmployeeId] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleLogin = async (e) => {
    e.preventDefault();
    setError("");

    // Basic validation
    if (!employeeId.trim() || !password.trim()) {
      setError("Employee ID and Password are required");
      return;
    }

    setLoading(true);

    try {
      const response = await fetch(
        "http://localhost:9091/askhr/api/v1/login",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            employeeId,
            password,
          }),
        }
      );

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.message || "Invalid Employee ID or Password");
      }

      // Expected backend response:
      // { message, employeeId, role, token }
      const userData = {
        employeeId: data.employeeId,
        role: data.role,
        token: data.token,
      };

      // Persist login data
      localStorage.setItem("askhr_user", JSON.stringify(userData));
      localStorage.setItem("askhr_token", data.token);

      // Notify parent
      updateIsLogin(userData);
    } catch (err) {
      console.error("Login failed:", err);
      setError(err.message || "Login failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-card">
      <div className="login-logo">
        <span className="logo-box">I</span> INTECH
        <p className="tagline">THE DESIRE TO PERFORM</p>
      </div>

      <form onSubmit={handleLogin}>
        <label>Employee ID</label>
        <input
          type="text"
          value={employeeId}
          onChange={(e) => setEmployeeId(e.target.value)}
          placeholder="Enter Employee ID"
          autoComplete="username"
        />

        <label className="password-label">
          Password <span className="choose-user">Choose a user</span>
        </label>
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="Enter Password"
          autoComplete="current-password"
        />

        <button type="submit" className="login-btn" disabled={loading}>
          {loading ? "Logging in..." : "Log in"}
        </button>
      </form>

      {error && <p className="error-text">{error}</p>}

      <p className="help-text">
        Please contact IT Support team related to the login issue.
        <br />
        Email To: <a href="#">IT Service Desk</a>
      </p>
    </div>
  );
};

export default LoginCard;
