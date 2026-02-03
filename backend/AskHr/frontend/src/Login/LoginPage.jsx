import Header from "./Header";
import MapSection from "./MapSection";
import LoginCard from "./LoginCard";
import "./login.css";
const LoginPage = (props) => {
  return (
    <>
      <Header />
      <div className="login-layout">
        <MapSection />
        <LoginCard updateIsLogin={props.updateIsLogin} />
      </div>
    </>
  );
};

export default LoginPage;
