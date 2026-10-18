// 테마 저장 키와, 첫 페인트 전에 <html data-theme>를 붙이는 인라인 스크립트. 서버(layout)와 클라이언트(ThemeToggle)가 함께 쓴다.

export const THEME_KEY = "uplog.theme";

export const THEME_SCRIPT = `try{var t=localStorage.getItem("${THEME_KEY}");if(t==="light"||t==="dark")document.documentElement.dataset.theme=t}catch(e){}`;
