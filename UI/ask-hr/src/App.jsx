import { useState, useEffect } from "react";
import Profile from "./dashboard/Profile";
import LoginPage from "./Login/LoginPage";
 

function App() {
  const [isLogin, setIsLogin] = useState(false);
  const [user, setUser] = useState(null);

  // Load saved user info on page refresh
  useEffect(() => {
    const savedUser = localStorage.getItem("user");
    if (savedUser) {
      setUser(JSON.parse(savedUser));
      setIsLogin(true);
    }
  }, []);

  const handleLoginSuccess = (userData) => {
    setUser(userData);
    setIsLogin(true);
    localStorage.setItem("user", JSON.stringify(userData));
  };

  return (
    <div>
      {isLogin ? (
        <Profile user={user} />
      ) : (
        <LoginPage updateIsLogin={handleLoginSuccess} />
      )}
    </div>
  );
}

export default App;