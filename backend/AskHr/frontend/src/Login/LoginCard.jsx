import { useState } from "react";

const LoginCard = (props) => {
  const [employeeId, setEmployeeId] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

 const handleLogin = async (e) => {
  e.preventDefault();
  setError("");

  // Validation
  if (!employeeId || !password) {
    setError("Employee ID and Password are required");
    return;
  }

  setLoading(true);

  try {
    // POST request to login API
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
        credentials: "include", // optional, for cookies
      }
    );

    // Handle error responses
    if (!response.ok) {
      const errData = await response.json().catch(() => ({}));
      throw new Error(errData.message || "Invalid Employee ID or Password");
    }

    // Parse response
    const data = await response.json();
    console.log("Login success:", data);

    // Create full user object
    const userData = {
      employeeId: data.employeeId,
      name: data.name || "",   // Ensure backend sends name
      email: data.email || "", // optional
    };

    // Save to localStorage
    localStorage.setItem("user", JSON.stringify(userData));

    // Notify parent component that login succeeded with user data
    props.updateIsLogin(userData);
  } catch (err) {
    console.error(err);
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
        />

        <label className="password-label">
          Password <span className="choose-user">Choose a user</span>
        </label>
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="Enter Password"
        />

        <button type="submit" className="login-btn" disabled={loading}>
          {loading ? "Logging in..." : "Log in"}
        </button>
      </form>

      {error && <p className="error-text">{error}</p>}

      <p className="help-text">
        Please contact IT Support team related to the login issue.<br />
        Email To: <a href="#">IT Service Desk</a>
      </p>
    </div>
  );
};

export default LoginCard;
