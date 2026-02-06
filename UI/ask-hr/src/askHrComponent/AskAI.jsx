import { useState, useEffect, useRef } from "react";
import "../askHrComponent/AskAI.css";
import askLogo from "../images/askLogos.png";
import Menu from "./Menu";
import parse from 'html-react-parser';
import DOMPurify from 'dompurify'; 
// Import sound files
import sendSound from "../sounds/send.mp3";
import receiveSound from "../sounds/whatsappSend.mp3";
import AudioButton from "../AudioToText/AudioButton";
import SuggestionChips from "../Suggestions/SuggestionChips";
import DownloadAttachmentButton from "../downlaodAttachment/DownloadAttachmentButton";
import { parseAIResponse } from "../utils/aiResponseParser";
import InlineDownloadToast from "../downlaodAttachment/InlineDownloadToast"; 

import { CONTEXT_CHIPS } from "../Suggestions/contextChips";
import { detectContext } from "../Suggestions/detectContext";


function AskAI({ user }) {
/* ================== STATE ================== */
const STORAGE_KEY = "chatHistory";
const LAST_SESSION_KEY = "lastSession";
const GLOBAL_CACHE_KEY = "globalChatCache";

const [currentSessionId, setCurrentSessionId] = useState(null);
const [searchQuery, setSearchQuery] = useState("");
const [searchResults, setSearchResults] = useState([]);

const [chatHistory, setChatHistory] = useState({});
const [showTooltip, setShowTooltip] = useState(true);
const [tooltipText, setTooltipText] = useState("Need help?");
const [open, setOpen] = useState(false);
const [message, setMessage] = useState("");
const [chat, setChat] = useState([]);
const [loading, setLoading] = useState(false);
const [abortController, setAbortController] = useState(null);
const [hasExitedChat, setHasExitedChat] = useState(false);
const [showSearch, setShowSearch] = useState(false);
const [undoStack, setUndoStack] = useState([]);
const [redoStack, setRedoStack] = useState([]);
const [showWelcome, setShowWelcome] = useState(true);
// Draggable AI button
const [aiPos, setAiPos] = useState({ x: window.innerWidth - 80, y: window.innerHeight - 100 });
const [dragging, setDragging] = useState(false);
const dragStart = useRef({ x: 0, y: 0 });
const dragThreshold = 5;

const chatEndRef = useRef(null);

// ---------- suggestions List----------
const [suggestionList, setSuggestionList] = useState(CONTEXT_CHIPS.DEFAULT);
const [suggestions, setSuggestions] = useState([
"Leave policy",
"Salary slip",
"WFH policy",
"Holiday list",
  "Insurance benefits",
  "confirm",
"Attendance issue",
]);
//for global chat caching
const normalizeQuery = (q) =>
  q
    ?.toLowerCase()
    .trim()
    .replace(/\s+/g, " ");

const loadGlobalCache = () => {
  try {
    const saved = localStorage.getItem(GLOBAL_CACHE_KEY);
    return saved ? JSON.parse(saved) : {};
  } catch {
    return {};
  }
};

const saveGlobalCache = (cacheObj) => {
  localStorage.setItem(GLOBAL_CACHE_KEY, JSON.stringify(cacheObj));
};
///////   global chat caching////////
const CHIP_MAP = {
"Leave policy": ["Casual leave", "Sick leave", "Apply leave"],
"Salary slip": ["Download slip", "CTC breakup", "Tax deduction"],
"WFH policy": ["Hybrid policy", "Approval process", "WFH days"],
"Holiday list": ["Public holidays", "Optional holidays"],
"Insurance benefits": ["Health insurance", "Dependents coverage"],
"Attendance issue": ["Missed punch", "Regularization"],
};

const handleChipSelect = (chipText) => {
// Append chip text to input
setMessage((prev) =>
prev.trim() ? `${prev} ${chipText}` : chipText
);

setSuggestions((prev) => {
// Remove clicked chip
const filtered = prev.filter((c) => c !== chipText);

// Get related chips
const related = CHIP_MAP[chipText] || [];

// Add new chips without duplicates
const merged = [...filtered, ...related].filter(
(chip, index, arr) => arr.indexOf(chip) === index
);

// Limit chip count (UI friendly)
return merged.slice(0, 6);
});
};

// ---------- LOGIN USER ----------
const username = user?.name || "";
const email = user?.employeeId || "";

 const updateSuggestionsByContext = (text) => {
  const ctx = detectContext(text);
  const newChips = CONTEXT_CHIPS[ctx] || CONTEXT_CHIPS.DEFAULT;

  // keep unique + limit
  setSuggestionList((prev) => {
    const merged = [...newChips, ...prev].filter(
      (chip, index, arr) => arr.indexOf(chip) === index
    );
    return merged.slice(0, 6);
  });
}; 

/* Group sessions by day (for History menu)*/
const getToday = () => new Date().toISOString().split("T")[0];
const groupSessionsByDate = (sessions) => {
  return Object.values(sessions).reduce((acc, session) => {
    acc[session.date] = acc[session.date] || [];
    acc[session.date].push(session);
    return acc;
  }, {});
};


const generateTitle = (text) =>
  text.split(" ").slice(0, 4).join(" ") + "...";



//======================================
/* ================== LOAD HISTORY ================== */
useEffect(() => {
  const saved = localStorage.getItem(STORAGE_KEY);
  if (saved) setChatHistory(JSON.parse(saved));
}, []);

useEffect(() => {
  const last = localStorage.getItem(LAST_SESSION_KEY);
  if (!last) return;

  const { username: u, sessionId } = JSON.parse(last);
  const session = chatHistory[u]?.sessions?.[sessionId];

  if (session) {
    setChat(session.messages);
    setCurrentSessionId(sessionId);
    setOpen(true);
  }
}, [chatHistory]);

useEffect(() => {
  if (!username || !currentSessionId || chat.length === 0) return;

  setChatHistory((prev) => {
    const updated = {
      ...prev,
      [username]: {
        ...prev[username],
        lastSessionId: currentSessionId,
        sessions: {
          ...prev[username]?.sessions,
          [currentSessionId]: {
            ...prev[username]?.sessions?.[currentSessionId],
            messages: chat,
          },
        },
      },
    };

    localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
    localStorage.setItem(
      LAST_SESSION_KEY,
      JSON.stringify({ username, sessionId: currentSessionId })
    );

    return updated;
  });
}, [chat, currentSessionId, username]);


/* ================== TOOLTIP AUTO HIDE ================== */
const [toast, setToast] = useState({ show: false, message: "", type: "info" });

const showToast = (message, type = "info") => {
  setToast({ show: true, message, type });
};

useEffect(() => {
if (!showTooltip) return;

const timer = setTimeout(() => setShowTooltip(false), 5000);
return () => clearTimeout(timer);
}, [showTooltip]);

/* ================== SCROLL ================== */
useEffect(() => {
chatEndRef.current?.scrollIntoView({ behavior: "smooth" });
}, [chat, loading]);

/* ================== DRAGGABLE BUTTON ================== */
const handleMouseDown = (e) => {
setDragging(true);
dragStart.current = { x: e.clientX, y: e.clientY };
};

const handleMouseMove = (e) => {
if (!dragging) return;
const dx = e.clientX - dragStart.current.x;
const dy = e.clientY - dragStart.current.y;
if (Math.abs(dx) > dragThreshold || Math.abs(dy) > dragThreshold) {
setAiPos((prev) => ({ x: prev.x + dx, y: prev.y + dy }));
dragStart.current = { x: e.clientX, y: e.clientY };
}
};

const handleMouseUp = () => setDragging(false);

useEffect(() => {
window.addEventListener("mousemove", handleMouseMove);
window.addEventListener("mouseup", handleMouseUp);
return () => {
window.removeEventListener("mousemove", handleMouseMove);
window.removeEventListener("mouseup", handleMouseUp);
};
}, [dragging]);

/* ================== SOUND ================== */
const playSound = (sound) => {
const audio = new Audio(sound);
audio.volume = 0.5;
audio.play();
};

/* ================== UNDO / REDO ================== */
const pushUndoStack = (prevChat) => {
setUndoStack((prev) => [...prev, JSON.parse(JSON.stringify(prevChat))]);
setRedoStack([]); // clear redo on new change
};

const handleUndo = () => {
if (undoStack.length === 0) return;
const prev = undoStack[undoStack.length - 1];
setRedoStack((prevRedo) => [...prevRedo, JSON.parse(JSON.stringify(chat))]);
setChat(prev);
setUndoStack((prev) => prev.slice(0, prev.length - 1));
};

const searchHistory = (query) => {
  if (!query) return setSearchResults([]);

  const results = [];

  Object.values(chatHistory[username]?.sessions || {}).forEach((s) => {
    s.messages.forEach((m) => {
      if (m.text.toLowerCase().includes(query.toLowerCase())) {
        results.push({
          sessionId: s.id,
          title: s.title,
          preview: m.text.slice(0, 80),
        });
      }
    });
  });

  setSearchResults(results);
};


const handleRedo = () => {
if (redoStack.length === 0) return;
const next = redoStack[redoStack.length - 1];
setUndoStack((prevUndo) => [...prevUndo, JSON.parse(JSON.stringify(chat))]);
setChat(next);
setRedoStack((prev) => prev.slice(0, prev.length - 1));
};

const copyLastAI = () => {
const lastAI = chat.filter((msg) => msg.role === "ai").pop();
if (lastAI) {
navigator.clipboard.writeText(lastAI.text);
alert("Last AI response copied to clipboard!");
}
};

const getTimeGreeting = () => {
const hour = new Date().getHours();
if (hour < 12) return "Good morning";
if (hour < 17) return "Good afternoon";
return "Good evening";
};

/* ================== GREETING ================== */
useEffect(() => {
if (open && chat.length === 0 && username) {
const greeting = getTimeGreeting();
setChat([
{
role: "ai",
text: `${greeting} ${username} 👋\nHow can I help you today?`,
time: new Date().toLocaleTimeString(),
},
]);
}
}, [open, username]);

useEffect(() => {
  if (!username) return;
  if (currentSessionId) return;

  const sessionId = `${getToday()}_${Date.now()}`;
  setCurrentSessionId(sessionId);

  setChatHistory((prev) => ({
    ...prev,
    [username]: {
      ...prev[username],
      sessions: {
        ...prev[username]?.sessions,
        [sessionId]: {
          id: sessionId,
          title: "New Chat",
          date: getToday(),
          createdAt: Date.now(),
          messages: [],
        },
      },
    },
  }));
}, [username, currentSessionId]);


/* ================== BACKEND CALL ================== */
const loadHistoryFromStorage = () => {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved ? JSON.parse(saved) : {};
  } catch (e) {
    return {};
  }
};


const callBackend = async () => {
  if (showWelcome) setShowWelcome(false);
  if (!message.trim()) return;

  pushUndoStack(chat);
  setMessage("");

  const time = new Date().toLocaleTimeString();
  const userText = message.trim();
  const normalized = normalizeQuery(userText);
  const globalCache = loadGlobalCache();
  if (globalCache[normalized]) {
  const cached = globalCache[normalized];

  // push cached AI response instantly
  // push user message FIRST
setChat((prev) => [...prev, { role: "user", text: userText, time }]);

if (globalCache[normalized]) {
  const cached = globalCache[normalized];

  setChat((prev) => [
    ...prev,
    {
      role: "ai",
      text: cached.message,
      time: new Date().toLocaleTimeString(),
      attachment: cached.attachment || null,
      cached: true,
    },
  ]);

  playSound(receiveSound);
  updateSuggestionsByContext(cached.message);
  return;
}


  playSound(receiveSound);

  // suggestions update
  updateSuggestionsByContext(cached.message);

  return; // ✅ stop backend call
}
  setMessage("");

  // ✅ Push user message first
  setChat((prev) => [...prev, { role: "user", text: userText, time }]);

  // ✅ Update suggestions based on user input immediately
  updateSuggestionsByContext(userText);

  // ✅ Play send sound
  playSound(sendSound);

  const controller = new AbortController();
  setAbortController(controller);
  setLoading(true);

  try {
    const res = await fetch(
      `http://localhost:9091/askhr/api/v1/search/chat?message=${encodeURIComponent(
        userText
      )}`,
      {
        method: "GET",
        signal: controller.signal,
        headers: {
          emailId: email,
        },
      }
    );

    const reader = res.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let aiText = "";

    let aiMessageStarted = false;

while (true) {
  const { value, done } = await reader.read();
  if (done) break;

  aiText += decoder.decode(value, { stream: true });
  const parsed = parseAIResponse(aiText);

  setChat((prev) => {
    // 🛑 DUPLICATE GREETING GUARD
    const isGreeting =
      parsed.message?.toLowerCase().includes("how can i help you");

    const alreadyGreeted = prev.some(
      (m) =>
        m.role === "ai" &&
        m.text?.toLowerCase().includes("how can i help you")
    );

    if (isGreeting && alreadyGreeted) {
      return prev; // ❌ skip duplicate greeting
    }

    const updated = [...prev];

    if (!aiMessageStarted) {
      updated.push({
        role: "ai",
        text: parsed.message,
        time: new Date().toLocaleTimeString(),
        attachment: parsed.attachment,
      });
      aiMessageStarted = true;
    } else {
      updated[updated.length - 1].text = parsed.message;
      updated[updated.length - 1].attachment = parsed.attachment;
    }

    return updated;
  });
}

    // ✅ receive sound after full response
    playSound(receiveSound);

 const finalParsed = parseAIResponse(aiText);
const updatedCache = loadGlobalCache();
updatedCache[normalized] = {
  message: finalParsed.message,
  attachment: finalParsed.attachment || null,
  savedAt: Date.now(),
};
saveGlobalCache(updatedCache);

    // ✅ Update suggestions based on final AI response (ONLY HERE)
    updateSuggestionsByContext(aiText);

  } catch (err) {
    if (err.name === "AbortError") {
      setChat((prev) => [
        ...prev,
        {
          role: "ai",
          text: "❌ Response stopped.",
          time: new Date().toLocaleTimeString(),
        },
      ]);
    } else {
      setChat((prev) => [
        ...prev,
        {
          role: "ai",
          text: "❌ Error occurred. Click to retry.",
          time: new Date().toLocaleTimeString(),
          failed: true,
          originalMessage: userText,
        },
      ]);
    }
  } finally {
    setLoading(false);
    setAbortController(null);
  }
};

/* ================== RETRY ================== */
const retryMessage = (msg) => {
pushUndoStack(chat);
setMessage(msg);
setChat((prev) => prev.filter((m) => m.originalMessage !== msg));
callBackend();
};

/* ================== KEYBOARD ================== */
const handleKeyDown = (e) => {
if (e.ctrlKey && e.key === "z") handleUndo();
if (e.ctrlKey && e.key === "y") handleRedo();
if (e.ctrlKey && e.key === "c") copyLastAI();
if (e.key === "Enter") callBackend();
};

/* ================== MENU FUNCTIONS ================== */
const handleClearChat = () => {
pushUndoStack(chat);
setChat([]);
};

const handleExportChat = () => {
const content = chat.map((msg) => `${msg.role.toUpperCase()}: ${msg.text}`).join("\n");
const blob = new Blob([content], { type: "text/plain" });
const url = URL.createObjectURL(blob);
const a = document.createElement("a");
a.href = url;
a.download = "chat.txt";
a.click();
URL.revokeObjectURL(url);
};

const closeChat = () => {
setHasExitedChat(true);
setOpen(false);
setTooltipText("Need more help?");
setShowTooltip(true);
};

const handleExitChat = () => {
pushUndoStack(chat);
// setChat([]);
closeChat();
};

const handleClearHistory = () => {
if (!window.confirm("Are you sure you want to clear all chat history?")) return;
setChatHistory({});
setChat([]);
localStorage.removeItem("chatHistory");
localStorage.removeItem("lastSession");
alert("All chat history cleared.");
};

const handleSaveSession = () => {
localStorage.setItem("chatSession", JSON.stringify(chat));
alert("Session saved!");
};

const handleLoadSession = () => {
const content = localStorage.getItem("chatSession");
if (content) setChat(JSON.parse(content));
else alert("No saved session found.");
};

const deleteHistoryUser = (user) => {
const updated = { ...chatHistory };
delete updated[user];
setChatHistory(updated);
localStorage.setItem("chatHistory", JSON.stringify(updated));
if (username === user) setChat([]);
};

const historyMenuItems = Object.values(
  loadHistoryFromStorage()[username]?.sessions || {}
).map((s) => ({
  label: `${s.date} • ${s.title}`,
  onClick: () => {
    const latest = loadHistoryFromStorage();
    const session = latest[username]?.sessions?.[s.id];

    setChat(session?.messages || []);
    setCurrentSessionId(s.id);
    setOpen(true);
  },
}));

const startNewChat = () => {
  if (!username) return;

  // get latest stored history
  const saved = localStorage.getItem(STORAGE_KEY);
  const latestHistory = saved ? JSON.parse(saved) : chatHistory;

  const sessions = latestHistory?.[username]?.sessions || {};
  const sessionCount = Object.keys(sessions).length;

  let title = "";

  // ✅ first session ever
  if (sessionCount === 0) {
    title = `Chat : ${username}`;
  } else {
    const nextGuest = getNextGuestNumber(latestHistory, username);
    title = `Chat : Guest ${nextGuest}`;
  }

  const sessionId = `${getToday()}_${Date.now()}`;

  setCurrentSessionId(sessionId);
  setChat([]);
  setShowWelcome(true);
  setOpen(true);

  setChatHistory((prev) => {
    const updated = {
      ...prev,
      [username]: {
        ...prev[username],
        lastSessionId: sessionId,
        sessions: {
          ...prev[username]?.sessions,
          [sessionId]: {
            id: sessionId,
            title,
            date: getToday(),
            createdAt: Date.now(),
            messages: [],
          },
        },
      },
    };

    localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
    localStorage.setItem(
      LAST_SESSION_KEY,
      JSON.stringify({ username, sessionId })
    );

    return updated;
  });
};
 

const getNextGuestNumber = (history, username) => {
  const sessions = history?.[username]?.sessions || {};
  const titles = Object.values(sessions).map((s) => s.title || "");

  // Match: "Chat : Guest 12"
  const nums = titles
    .map((t) => {
      const m = t.match(/^Chat\s*:\s*Guest\s+(\d+)$/i);
      return m ? parseInt(m[1], 10) : null;
    })
    .filter((n) => n !== null);

  const max = nums.length ? Math.max(...nums) : 0;
  return max + 1;
};

 
const fileMenuItems = [
{ label: "New Chat", onClick: () => { pushUndoStack(chat); startNewChat(); } },
{ label: "Clear Chat", onClick: handleClearChat },
{ label: "Export Chat", onClick: handleExportChat },
{ label: "Exit Chat", onClick: handleExitChat },
{ label: "Clear History", onClick: handleClearHistory },
];

const editMenuItems = [
{ label: "Undo", onClick: handleUndo },
{ label: "Redo", onClick: handleRedo },
{ label: "Copy Last AI Response", onClick: copyLastAI },
];

const searchMenuItems = [
{ label: "Find in Chat", onClick: () => setShowSearch(true) },
{ label: "Find Next", onClick: () => alert("Find Next clicked!") },
];

const sessionMenuItems = [
{ label: "Save Session", onClick: handleSaveSession },
{ label: "Load Session", onClick: handleLoadSession },
{ label: "Clear Session", onClick: handleClearChat },
];

const helpMenuItems = [
{ label: "Documentation", onClick: () => window.open("https://example.com/docs", "_blank") },
{ label: "About", onClick: () => alert("HR Help Assistant v1.0 by Abhinav Kumar") },
];

/* ================== JSX ================== */
if (!open) {
return (
<div
className="ai-tooltip-wrapper"
style={{ left: aiPos.x, top: aiPos.y, position: "fixed" }}
onMouseDown={handleMouseDown}
onClick={() => setOpen(true)}
>
{showTooltip && (
<div className="ai-tooltip">
<button className="ai-tooltip-close" onClick={(e) => { e.stopPropagation(); setShowTooltip(false); }}>✕</button>
<div className="ai-tooltip-title">{tooltipText}</div>
<div className="ai-tooltip-sub">Get instant answers to your queries.</div>
<span className="ai-tooltip-arrow" />
</div>
)}
<div className="floating-icon">AI</div>
</div>
);
}

return (
<div className="app-root">
<div className="container open">
<div className="close-btn" onClick={closeChat}>✕</div>

{/* HEADER */}
<div className="header">
<div className="icon"><img src={askLogo} alt="AI" /></div>
<div className="title">HR Help Assistant {username && `(${username})`}</div>
</div>

{/* MENU BAR */}
<div className="menu-bar">
<Menu title="File" items={fileMenuItems} />
<Menu title="Edit" items={editMenuItems} />
<Menu title="History" items={historyMenuItems} />
<Menu title="Search" items={searchMenuItems} />
<Menu title="Session" items={sessionMenuItems} />
<Menu title="Help" items={helpMenuItems} />
</div>
<div className="response-area">
{showWelcome ? (
<div className="welcome-screen">
<h1>
<strong>{getTimeGreeting()}</strong> {user?.name}
</h1>
<p>How can I help you today?</p>
</div>
) : (
<div className="chat-body">
{chat.map((msg, i) => (
  <div
    key={i}
    className={`chat-bubble ${msg.role} ${msg.failed ? "retry" : ""}`}
  >
    {parse(DOMPurify.sanitize(msg.text))}

    {/* download icon */}
    {msg.role === "ai" && msg.attachment && (
  <DownloadAttachmentButton
    attachment={msg.attachment}
    msgIndex={i}
    setChat={setChat}
  />
)}

  {msg.role === "ai" && (
  <InlineDownloadToast
    state={msg.downloadState}
    message={msg.downloadMessage}
    onRetry={() => {
      // simulate click download again
      setChat((prev) => {
        const updated = [...prev];
        if (!updated[i]) return prev;
        updated[i] = {
          ...updated[i],
          retryDownload: Date.now(), // trigger
        };
        return updated;
      });
    }}
  />
)}

    <div className="timestamp">{msg.time}</div>
  </div>
))}
{loading && (
<div className="chat-bubble ai">
<div className="typing">
<span className="dot" />
<span className="dot" />
<span className="dot" />
</div>
</div>
)}
<div ref={chatEndRef} />
</div>
)}
</div>


{/* FOOTER */}
<div className="footer">
<input
value={message}
onChange={(e) => setMessage(e.target.value)}
onKeyDown={handleKeyDown}
placeholder="How can I help you today?"
/>
<AudioButton onTranscribe={(text) => setMessage(text)} />
<button
className={loading ? "pause" : ""}
onClick={() => {
if (loading && abortController) abortController.abort();
else callBackend();
}}
>
{loading ? "Pause" : "Ask"}
</button>
</div>
<SuggestionChips
suggestions={suggestionList}
onSelect={handleChipSelect}
/>
<div className="footer-note">&copy; Developed by Abhinav Kumar @ 2026</div>
</div>
  {/* ✅ ADD TOAST HERE (outside container but inside app-root) */}
     
</div>
);
}


export default AskAI;