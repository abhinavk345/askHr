import React, { useState, useEffect } from "react";
import "../AudioToText/AudioButton.css";
import dictate from "../images/dictate.png";
function AudioButton({ onTranscribe }) {
  const [listening, setListening] = useState(false);
  const [recognition, setRecognition] = useState(null);

  useEffect(() => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      alert("Your browser does not support speech recognition.");
      return;
    }

    const recog = new SpeechRecognition();
    recog.continuous = false;
    recog.interimResults = false;
    recog.lang = "en-US";

    recog.onstart = () => setListening(true);
    recog.onend = () => setListening(false);

    recog.onresult = (event) => {
      const transcript = event.results[0][0].transcript;
      onTranscribe(transcript);
    };

    setRecognition(recog);
  }, [onTranscribe]);

  const handleClick = () => {
    if (!recognition) return;
    if (listening) recognition.stop();
    else recognition.start();
  };

  return (
    <button
      className={`audio-button ${listening ? "listening" : ""}`}
      onClick={handleClick}
      title={listening ? "Listening..." : "Click to speak"}
    >
      <img src ={dictate}  alt="Mic" style={{ width: "24px", height: "24px" }}/>
    </button>
  );
}

export default AudioButton;
