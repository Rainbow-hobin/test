/**
 * 苍穹外卖网页版 - 工具函数
 */
const Util = {
  esc(str) {
    return String(str == null ? "" : str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  },
  money(n) {
    return Number(n || 0).toFixed(2);
  },
  img(src) {
    if (!src) return "images/dish/friedrice.svg";
    if (/^(https?:)?\/\//.test(src)) return src;
    return src.replace(/^\//, "");
  },
  fmtTime(str) {
    if (!str) return "";
    return String(str).replace("T", " ").substring(0, 19);
  },
  orderStatusText(status) {
    return {
      1: "待付款",
      2: "待接单",
      3: "已接单",
      4: "派送中",
      5: "已完成",
      6: "已取消",
      7: "已退款"
    }[status] || "未知状态";
  },
  roleText(role) {
    return role === 2 ? "商家" : "用户";
  },
  toast(msg, ms) {
    const el = document.getElementById("toast");
    el.textContent = msg;
    el.classList.add("show");
    clearTimeout(this._toastTimer);
    this._toastTimer = setTimeout(() => el.classList.remove("show"), ms || 1800);
  },
  confirm(text) {
    return new Promise(resolve => {
      const mask = document.getElementById("confirm-mask");
      document.getElementById("confirm-text").textContent = text;
      mask.classList.remove("hidden");
      const ok = () => { cleanup(); resolve(true); };
      const cancel = () => { cleanup(); resolve(false); };
      const cleanup = () => {
        mask.classList.add("hidden");
        document.getElementById("confirm-ok").removeEventListener("click", ok);
        document.getElementById("confirm-cancel").removeEventListener("click", cancel);
      };
      document.getElementById("confirm-ok").addEventListener("click", ok);
      document.getElementById("confirm-cancel").addEventListener("click", cancel);
    });
  }
};
