import Header from "./Header";
import MapSection from "./MapSection";
import LoginCard from "./LoginCard";
import "./login.css";

const LoginPage = ({ updateIsLogin }) => {
  return (
    <>
      <Header />
      <div className="login-layout">
        <MapSection />
        <LoginCard updateIsLogin={updateIsLogin} />
      </div>
    </>
  );
};

export default LoginPage;
