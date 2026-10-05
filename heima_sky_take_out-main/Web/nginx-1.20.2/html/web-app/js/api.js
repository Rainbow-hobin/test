/**
 * 苍穹外卖网页版 - API 层（用户端）
 * 后端地址：同源走 nginx 代理（/user），也可改为 http://localhost:8080
 */
const API_BASE = "";
const TOKEN_KEY = "sky_web_token";
const USER_KEY = "sky_web_user";

const Api = {
  getToken() {
    return localStorage.getItem(TOKEN_KEY) || "";
  },
  getUser() {
    try {
      return JSON.parse(localStorage.getItem(USER_KEY) || "null");
    } catch (e) {
      return null;
    }
  },
  setAuth(token, user) {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  },
  setUser(user) {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  },
  clearAuth() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  },

  async request(method, path, body) {
    const headers = { "Content-Type": "application/json" };
    const token = this.getToken();
    if (token) headers["authentication"] = token;

    const res = await fetch(API_BASE + path, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined
    });

    if (res.status === 401) {
      // token 失效
      this.clearAuth();
      location.hash = "#/login";
      throw new Error("登录已过期，请重新登录");
    }

    let data = null;
    try {
      data = await res.json();
    } catch (e) {
      data = null;
    }
    if (data && data.code === 0) {
      throw new Error(data.msg || "请求失败");
    }
    return data ? data.data : null;
  },

  get(path) { return this.request("GET", path); },
  post(path, body) { return this.request("POST", path, body); },
  put(path, body) { return this.request("PUT", path, body); },
  del(path) { return this.request("DELETE", path); },

  // ============ 认证 ============
  login(username, password) {
    return this.post("/user/user/login", { username, password, role: 1 });
  },
  register(payload) {
    return this.post("/user/user/register", payload);
  },

  // ============ 个人信息 ============
  userInfo() { return this.get("/user/user/info"); },
  updateUserInfo(payload) { return this.put("/user/user/info", payload); },

  // ============ 点餐 ============
  shopStatus() { return this.get("/user/shop/status"); },
  categories(type) { return this.get("/user/category/list" + (type ? "?type=" + type : "")); },
  dishes(categoryId) { return this.get("/user/dish/list?categoryId=" + categoryId); },
  setmeals(categoryId) { return this.get("/user/setmeal/list?categoryId=" + categoryId); },
  setmealDishes(id) { return this.get("/user/setmeal/dish/" + id); },

  // ============ 购物车 ============
  cartList() { return this.get("/user/shoppingCart/list"); },
  cartAdd(payload) { return this.post("/user/shoppingCart/add", payload); },
  cartSub(payload) { return this.post("/user/shoppingCart/sub", payload); },
  cartClean() { return this.del("/user/shoppingCart/clean"); },

  // ============ 地址 ============
  addressList() { return this.get("/user/addressBook/list"); },
  addressSave(payload) { return this.post("/user/addressBook", payload); },
  addressUpdate(payload) { return this.put("/user/addressBook", payload); },
  addressDelete(id) { return this.del("/user/addressBook?id=" + id); },
  addressDefault(id) { return this.put("/user/addressBook/default", { id }); },

  // ============ 订单 ============
  orderSubmit(payload) { return this.post("/user/order/submit", payload); },
  orderPay(payload) { return this.put("/user/order/payment", payload); },
  orderDetail(id) { return this.get("/user/order/orderDetail/" + id); },
  orderHistory(page, pageSize, status) {
    let url = "/user/order/historyOrders?page=" + page + "&pageSize=" + pageSize;
    if (status != null && status !== "") url += "&status=" + status;
    return this.get(url);
  },
  orderCancel(id) { return this.put("/user/order/cancel/" + id); },
  orderRepetition(id) { return this.post("/user/order/repetition/" + id); },
  orderReminder(id) { return this.get("/user/order/reminder/" + id); },

  // ============ 聊天 ============
  chatConversations() { return this.get("/user/chat/conversations"); },
  chatHistory() { return this.get("/user/chat/history"); },
  chatRead() { return this.put("/user/chat/read"); },
  chatUnreadCount() { return this.get("/user/chat/unreadCount"); }
};
