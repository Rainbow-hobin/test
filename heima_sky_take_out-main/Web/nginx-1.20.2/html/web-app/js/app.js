/**
 * 苍穹外卖网页版 - 主应用
 * 用户端页面（点餐/购物车/下单/支付/历史订单/地址/我的）
 * 商家功能请使用原版 PC 后台：/admin/
 */
(function () {
  "use strict";

  const app = document.getElementById("app");
  const state = {
    user: Api.getUser(),
    route: "menu",
    params: {},
    cart: [],
    categories: [],
    typeIndex: 0,
    shopStatus: 1,
    currentOrder: null
  };

  // ==================== 基础渲染 ====================
  function render(html, opts) {
    opts = opts || {};
    app.innerHTML = html;
    document.querySelector(".page-shell").style.height = "98vh";
    if (opts.title) document.title = opts.title + " · 苍穹外卖";
  }

  function navbar(title, opts) {
    opts = opts || {};
    return '<div class="navbar">'
      + (opts.back ? '<button class="nav-back" onclick="history.back()">‹</button>' : "")
      + '<span>' + Util.esc(title) + '</span>'
      + (opts.right ? '<button class="nav-right" ' + (opts.rightAction || "") + '>' + Util.esc(opts.right) + "</button>" : "")
      + "</div>";
  }

  // ==================== 线性图标库（24×24，stroke=currentColor，统一 1.8 线宽） ====================
  function svgIcon(inner, sw) {
    return '<svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor"'
      + (sw ? ' stroke-width="' + sw + '"' : "")
      + ' stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + inner + "</svg>";
  }
  const Ico = {
    utensils: '<path d="M3 2v7c0 1.1.9 2 2 2a2 2 0 0 0 2-2V2"/><path d="M5 2v20"/><path d="M7 2v7a2 2 0 0 0 2 2c1.1 0 2-.9 2-2V2"/><path d="M21 15V2a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3Zm0 0v7"/>',
    receipt: '<path d="M5 2.5v19l2-1.2 2 1.2 2-1.2 2 1.2 2-1.2 2 1.2V2.5l-2 1.2-2-1.2-2 1.2-2-1.2-2 1.2z"/><path d="M9 8h6"/><path d="M9 11.5h6"/><path d="M9 15h4"/>',
    chat: '<path d="M21 11.6a8.4 8.4 0 0 1-8.5 8.4 8.6 8.6 0 0 1-3.9-.9L3 21l1.9-5.6A8.4 8.4 0 1 1 21 11.6z"/><circle cx="9.2" cy="11.6" r="1" fill="currentColor" stroke="none"/><circle cx="12" cy="11.6" r="1" fill="currentColor" stroke="none"/><circle cx="14.8" cy="11.6" r="1" fill="currentColor" stroke="none"/>',
    user: '<circle cx="12" cy="8" r="4"/><path d="M4.5 20c0-4 3.6-6 7.5-6s7.5 2 7.5 6"/>',
    userEdit: '<path d="M11.5 15.2H7a4 4 0 0 0-4 4v1.3"/><circle cx="10" cy="7.2" r="4"/><path d="M21.1 6.9a1 1 0 0 0-3.95-3.95L3.9 16.2c-.25.25-.42.57-.5.92l-1.25 4.1c-.1.33.2.63.53.53l4.1-1.25c.35-.08.67-.25.92-.5z"/>',
    pin: '<path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0z"/><circle cx="12" cy="10" r="3"/>',
    logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="M16 17l5-5-5-5"/><path d="M21 12H9"/>',
    cart: '<circle cx="9" cy="20" r="1.5"/><circle cx="18" cy="20" r="1.5"/><path d="M2 3h2.2l2.3 12.4a1.8 1.8 0 0 0 1.8 1.6h8.9a1.8 1.8 0 0 0 1.8-1.5L21 7.2H5.2"/>',
    scooter: '<circle cx="5.8" cy="17.5" r="3.2"/><circle cx="18.2" cy="17.5" r="3.2"/><path d="M5.8 17.5h6.7l2.3-7.3h3.4"/><path d="M9.8 10.2h3.6"/><path d="M14.8 5.7h2.7"/>',
    clock: '<circle cx="12" cy="12" r="8.6"/><path d="M12 7.4V12l3.3 2"/>',
    store: '<path d="M3.5 9.2 5 4h14l1.5 5.2"/><path d="M4.5 9.4a2.7 2.7 0 0 0 5 0 2.7 2.7 0 0 0 5 0 2.7 2.7 0 0 0 5 0"/><path d="M5 10.5V20h14v-9.5"/><path d="M9.5 20v-4.8h5V20"/>',
    bot: '<rect x="4.2" y="8.5" width="15.6" height="10.5" rx="3.2"/><path d="M12 8.5V5"/><circle cx="12" cy="3.8" r="1.1" fill="currentColor" stroke="none"/><circle cx="9.4" cy="13.2" r="1.05" fill="currentColor" stroke="none"/><circle cx="14.6" cy="13.2" r="1.05" fill="currentColor" stroke="none"/><path d="M9.6 16.2h4.8"/><path d="M2.8 12.8v2.6"/><path d="M21.2 12.8v2.6"/>',
    headset: '<path d="M4 14v-1.8a8 8 0 0 1 16 0V14"/><rect x="2.6" y="13.6" width="3.8" height="6" rx="1.7"/><rect x="17.6" y="13.6" width="3.8" height="6" rx="1.7"/><path d="M19.8 19.6v.6a3.4 3.4 0 0 1-3.4 3.4H14"/>',
    alert: '<path d="M12 3.5 21.5 19.5h-19z"/><path d="M12 10v4.2"/><circle cx="12" cy="17.2" r="1" fill="currentColor" stroke="none"/>',
    pencil: '<path d="M12 20h9"/><path d="M16.5 3.6a2.1 2.1 0 0 1 2.9 2.9L7 19l-4 1 1-4z"/>',
    trash: '<path d="M3.5 6h17"/><path d="M8.5 6V4.5a1 1 0 0 1 1-1h5a1 1 0 0 1 1 1V6"/><path d="M18.5 6l-.8 13.3a2 2 0 0 1-2 1.7H8.3a2 2 0 0 1-2-1.7L5.5 6"/><path d="M10 10.5v6"/><path d="M14 10.5v6"/>',
    camera: '<path d="M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z"/><circle cx="12" cy="13" r="3"/>',
    search: '<circle cx="11" cy="11" r="7"/><path d="M20 20l-4.3-4.3"/>'
  };
  Object.keys(Ico).forEach(function (k) { Ico[k] = svgIcon(Ico[k], 1.8); });

  // ==================== 头像 ====================
  // 设置了自定义头像则显示图片；未设置时保持原方案（姓名首字 / 默认用户图标）
  function avatarInner(user) {
    const url = Api.getAvatar();
    if (url) return '<img src="' + url + '" alt="头像">';
    return user && user.name ? Util.esc(user.name[0]) : Ico.user;
  }

  // 读取本地图片 → 居中裁剪正方形 → 压缩为 256px JPEG dataURL
  function fileToAvatarDataUrl(file) {
    return new Promise(function (resolve, reject) {
      if (!file || !/^image\//.test(file.type)) { reject(new Error("请选择图片文件")); return; }
      if (file.size > 8 * 1024 * 1024) { reject(new Error("图片不能超过 8MB")); return; }
      const reader = new FileReader();
      reader.onerror = function () { reject(new Error("图片读取失败")); };
      reader.onload = function () {
        const img = new Image();
        img.onerror = function () { reject(new Error("图片解析失败")); };
        img.onload = function () {
          const S = 256;
          const canvas = document.createElement("canvas");
          canvas.width = S; canvas.height = S;
          const ctx = canvas.getContext("2d");
          const side = Math.min(img.width, img.height);
          ctx.drawImage(img, (img.width - side) / 2, (img.height - side) / 2, side, side, 0, 0, S, S);
          resolve(canvas.toDataURL("image/jpeg", 0.85));
        };
        img.src = reader.result;
      };
      reader.readAsDataURL(file);
    });
  }

  // 底部主导航：常驻于 #app 之外，只构建一次，切页仅切换高亮，DOM 不销毁不闪烁
  const TAB_DEFS = [
    { key: "menu", label: "点餐", ico: "utensils" },
    { key: "history", label: "订单", ico: "receipt" },
    { key: "messages", label: "消息", ico: "chat" },
    { key: "my", label: "我的", ico: "user" }
  ];
  // 子页面归属哪个主 tab（用于非主 tab 页保持对应高亮）
  const TAB_HIGHLIGHT = {
    menu: "menu", order: "menu",
    history: "history", pay: "history", success: "history",
    messages: "messages", chat: "messages",
    my: "my", address: "my", addressForm: "my", profile: "my"
  };
  let tabbarInited = false;
  function syncTabbar(routeName) {
    const bar = document.getElementById("global-tabbar");
    const shell = document.querySelector(".page-shell");
    if (!bar || !shell) return;
    if (routeName === "login" || routeName === "register") {
      bar.classList.add("hidden");
      shell.classList.remove("has-tabbar");
      return;
    }
    bar.classList.remove("hidden");
    shell.classList.add("has-tabbar");
    if (!tabbarInited) {
      bar.innerHTML = TAB_DEFS.map(t =>
        '<div class="tab-item" data-key="' + t.key + '" onclick="App.go(\'' + t.key + '\')">'
        + '<span class="tab-ico-wrap"><span class="tab-ico">' + Ico[t.ico] + "</span>"
        + (t.key === "messages" ? '<span class="tab-badge hidden" id="msg-badge">0</span>' : "")
        + "</span><span>" + t.label + "</span></div>"
      ).join("");
      tabbarInited = true;
    }
    const active = TAB_HIGHLIGHT[routeName];
    bar.querySelectorAll(".tab-item").forEach(el =>
      el.classList.toggle("active", el.dataset.key === active));
  }

  // 消息 tab 未读红点
  function setMsgBadge(count) {
    const badge = document.getElementById("msg-badge");
    if (!badge) return;
    count = Number(count) || 0;
    badge.textContent = count > 99 ? "99+" : String(count);
    badge.classList.toggle("hidden", count === 0);
  }
  async function refreshUnread() {
    if (!Api.getUser()) return;
    try { setMsgBadge(await Api.chatUnreadCount()); } catch (e) { /* 忽略红点失败 */ }
  }

  function emptyTip(ico, text, actionHtml) {
    return '<div class="empty-tip"><div class="em-ico">' + (Ico[ico] || ico) + '</div><div>' + Util.esc(text) + "</div>"
      + (actionHtml ? '<div style="margin-top:16px;">' + actionHtml + "</div>" : "")
      + "</div>";
  }

  function guard() {
    const user = Api.getUser();
    if (!user) { location.hash = "#/login"; return false; }
    return true;
  }

  // ==================== 路由 ====================
  const routes = {
    login: pageLogin,
    register: pageRegister,
    menu: pageMenu,
    order: pageOrder,
    pay: pagePay,
    success: pageSuccess,
    address: pageAddress,
    addressForm: pageAddressForm,
    history: pageHistory,
    messages: pageMessages,
    chat: pageChat,
    my: pageMy,
    profile: pageProfile
  };

  function parseHash() {
    const raw = location.hash.replace(/^#\/?/, "") || "menu";
    const parts = raw.split("?");
    return { name: parts[0] || "menu", params: Object.fromEntries(new URLSearchParams(parts[1] || "")) };
  }

  function router() {
    const { name, params } = parseHash();
    const fn = routes[name] || pageMenu;
    // 登录后全局保持聊天长连接（任意页面都能实时收消息/更新红点），登出时关闭
    if (name === "login" || name === "register" || !Api.getUser()) closeChatSocket();
    else ensureChatSocket();
    state.route = name;
    state.params = params;
    fn(params);
    syncTabbar(name);
    refreshUnread();
    app.scrollTop = 0;
  }

  function go(name, params) {
    const qs = params ? "?" + new URLSearchParams(params).toString() : "";
    const h = "#/" + name + qs;
    if (location.hash === h) {
      router();
    } else {
      location.hash = h;
    }
  }

  window.App = { go, state };

  // ==================== 登录 / 注册 ====================
  function authLayout(brand) {
    return brand + '<div class="auth-card"><div id="auth-body"></div></div>';
  }

  function authBody(mode) {
    const isLogin = mode === "login";
    const fields = isLogin
      ? '<div class="field"><label>用户名</label><input id="a-username" placeholder="请输入用户名" autocomplete="username"></div>'
        + '<div class="field"><label>密码</label><input id="a-password" type="password" placeholder="请输入密码" autocomplete="current-password"></div>'
      : '<div class="field"><label>用户名</label><input id="a-username" placeholder="设置用户名（登录用）" autocomplete="username"></div>'
        + '<div class="field"><label>密码</label><input id="a-password" type="password" placeholder="设置密码" autocomplete="new-password"></div>'
        + '<div class="field"><label>昵称</label><input id="a-name" placeholder="你的称呼（可留空）"></div>'
        + '<div class="field"><label>手机号</label><input id="a-phone" placeholder="手机号（可留空）"></div>';

    return '<div class="auth-form">' + fields
      + '<button class="btn-primary" onclick="App.submitAuth(\'' + mode + '\')">' + (isLogin ? "登 录" : "注 册") + "</button>"
      + '</div><div class="auth-switch">'
      + (isLogin ? "还没有账号？<a href='#/register'>立即注册</a>" : "已有账号？<a href='#/login'>直接登录</a>")
      + "</div>";
  }

  function pageLogin() {
    const brand = '<div class="auth-brand"><img src="images/restaurant/logo.png" alt="logo"><h1>苍穹外卖</h1><p>网页版 · 个人练习项目</p></div>';
    render('<div class="auth-page">' + authLayout(brand) + "</div>");
    document.getElementById("auth-body").innerHTML = authBody("login");
  }

  function pageRegister() {
    const brand = '<div class="auth-brand"><img src="images/restaurant/logo.png" alt="logo"><h1>苍穹外卖</h1><p>注册新账号</p></div>';
    render('<div class="auth-page">' + authLayout(brand) + "</div>");
    document.getElementById("auth-body").innerHTML = authBody("register");
  }

  // ==================== 点餐首页 ====================
  async function pageMenu() {
    if (!guard()) return;
    render('<div id="menu-root"></div>', { title: "苍穹外卖" });
    const root = document.getElementById("menu-root");
    root.innerHTML = '<div class="empty-tip">加载中…</div>';
    try {
      const [status, cats] = await Promise.all([Api.shopStatus(), Api.categories()]);
      state.shopStatus = status;
      state.categories = cats || [];
      // 一次性加载全部菜品/套餐并缓存
      state.dishMap = {};
      for (const cat of state.categories) {
        const list = cat.type === 2 ? await Api.setmeals(cat.id) : await Api.dishes(cat.id);
        (list || []).forEach(d => {
          d._isSetmeal = cat.type === 2;
          state.dishMap[dishKey(d)] = d;
        });
      }
      const cart = await Api.cartList();
      state.cart = cart || [];
      root.innerHTML = menuHtml();
      bindMenuEvents();
    } catch (e) {
      root.innerHTML = emptyTip("alert", e.message || "加载失败", '<button class="btn-primary" onclick="App.go(\'menu\')">重新加载</button>');
    }
  }

  function dishesForType(index) {
    const cat = state.categories[index];
    if (!cat) return [];
    return Object.values(state.dishMap).filter(d => d.categoryId === cat.id);
  }

  function dishKey(dish) {
    return (dish._isSetmeal ? "s" : "d") + "-" + dish.id;
  }

  function menuHtml() {
    const dishes = dishesForType(state.typeIndex);
    const cart = state.cart || [];
    const total = cart.reduce((s, c) => s + c.amount * c.number, 0);
    const count = cart.reduce((s, c) => s + c.number, 0);
    const status = state.shopStatus === 1;

    return '<div class="menu-top">'
      + '<div class="menu-shop">'
      + '<img class="shop-logo" src="images/restaurant/logo.png" alt="logo">'
      + '<div><div class="shop-name">苍穹外卖'
      + '<span class="shop-status' + (status ? "" : " closed") + '">' + (status ? "营业中" : "休息中") + "</span></div>"
      + '<div class="shop-meta"><span>' + Ico.pin + '距离1.5km</span><span>' + Ico.scooter + '配送费6元</span><span>' + Ico.clock + '预计12min</span></div>'
      + '<div class="shop-address">北京市朝阳区新街大道一号楼8层</div>'
      + "</div></div>"
      + '<button class="shop-chat-btn" onclick="App.go(\'chat\')">' + Ico.chat + '联系商家</button>'
      + "</div>"
      + '<div class="menu-search" id="menu-search">'
      + '<span class="search-ico">' + Ico.search + '</span>'
      + '<input id="menu-search-input" placeholder="搜索菜品、套餐…" autocomplete="off">'
      + '<span class="search-clear" id="search-clear">×</span>'
      + '<div class="menu-search-results" id="menu-search-results"></div>'
      + "</div>"
      + '<div class="menu-body">'
      + '<div class="menu-types">' + state.categories.map((c, i) =>
        '<div class="type-item' + (i === state.typeIndex ? " active" : "") + '" data-idx="' + i + '">' + Util.esc(c.name) + "</div>"
      ).join("") + "</div>"
      + '<div class="menu-dishes">'
      + (dishes.length === 0 ? '<div class="empty-tip">该分类下暂无商品</div>' : dishes.map(d => dishHtml(withCartNumber(d))).join("") + '<div style="height:96px;"></div>')
      + "</div></div>"
      + '<div class="mask-layer hidden" id="menu-mask"></div>'
      + '<div class="cart-pop hidden" id="cart-pop"></div>'
      + '<div class="spec-pop hidden" id="spec-pop"></div>'
      + cartBarHtml(total, count);
  }

  function withCartNumber(dish) {
    const item = (state.cart || []).find(c =>
      dish._isSetmeal ? c.setmealId === dish.id : c.dishId === dish.id
    );
    dish._number = item ? item.number : 0;
    return dish;
  }

  function dishHtml(dish) {
    const isSetmeal = !!dish._isSetmeal;
    return '<div class="dish" data-key="' + dishKey(dish) + '" data-dish="' + dish.id + '" data-type="' + (isSetmeal ? 2 : 1) + '">'
      + '<img src="' + Util.img(dish.image) + '" alt="' + Util.esc(dish.name) + '">'
      + '<div class="dish-info">'
      + '<div class="dish-name">' + Util.esc(dish.name) + "</div>"
      + '<div class="dish-desc">' + Util.esc(dish.description || dish.name) + "</div>"
      + '<div class="dish-sales">月售 0</div>'
      + '<div class="dish-bottom">'
      + '<div class="dish-price">￥' + Util.money(dish.price) + "</div>"
      + '<div data-id="' + dish.id + '">' + dishStepperHtml(dish, isSetmeal) + "</div>"
      + "</div></div></div>";
  }

  function dishStepperHtml(dish, isSetmeal) {
    const closed = state.shopStatus !== 1;
    if (isSetmeal) {
      return dish._number > 0
        ? '<div class="stepper"><button class="step-sub" data-act="sub">−</button><span class="step-num">' + dish._number + '</span><button class="step-add' + (closed ? " disabled" : "") + '" data-act="add">+</button></div>'
        : '<button class="spec-btn' + (closed ? " disabled" : "") + '" data-act="spec">+</button>';
    }
    if (dish.flavors && dish.flavors.length > 0) {
      return dish._number > 0
        ? '<div class="stepper"><button class="step-sub" data-act="sub">−</button><span class="step-num">' + dish._number + '</span><button class="step-add' + (closed ? " disabled" : "") + '" data-act="add">+</button></div>'
        : '<button class="spec-btn' + (closed ? " disabled" : "") + '" data-act="spec">+</button>';
    }
    return '<div class="stepper">'
      + (dish._number > 0 ? '<button class="step-sub" data-act="sub">−</button>' : "")
      + (dish._number > 0 ? '<span class="step-num">' + dish._number + "</span>" : "")
      + '<button class="step-add' + (closed ? " disabled" : "") + '" data-act="add">+</button></div>';
  }

  function cartBarHtml(total, count) {
    const closed = state.shopStatus !== 1;
    return '<div class="cart-bar">'
      + '<div class="cart-left" id="cart-open">'
      + '<span class="cart-ico">' + Ico.cart + '</span>'
      + (count > 0 ? '<span class="cart-num">' + count + "</span>" : "")
      + '<span class="cart-total"><small>￥</small>' + Util.money(total) + "</span>"
      + '</div><button class="cart-go' + (count === 0 || closed ? " disabled" : "") + '" id="cart-go">' + (closed ? "已打烊" : "去结算") + "</button></div>";
  }

  function bindMenuEvents() {
    // 分类切换
    document.querySelectorAll(".menu-types .type-item").forEach(el => {
      el.addEventListener("click", () => {
        state.typeIndex = Number(el.dataset.idx);
        document.querySelectorAll(".menu-types .type-item").forEach(x => x.classList.toggle("active", x === el));
        const list = dishesForType(state.typeIndex);
        document.querySelector(".menu-dishes").innerHTML = list.length
          ? list.map(d => dishHtml(withCartNumber(d))).join("") + '<div style="height:96px;"></div>'
          : '<div class="empty-tip">该分类下暂无商品</div>';
      });
    });

    // 事件委托：购物车加减 / 规格
    document.querySelector(".menu-dishes").addEventListener("click", async e => {
      const btn = e.target.closest("[data-act]");
      if (!btn) return;
      const dishEl = btn.closest(".dish");
      const dish = state.dishMap[dishEl.dataset.key];
      if (!dish) return;
      const act = btn.dataset.act;
      if ((act === "add" || act === "spec") && state.shopStatus !== 1) { Util.toast("店铺已打烊，暂不接受点餐"); return; }
      if (act === "add") await cartAdd(dish, null, true);
      else if (act === "sub") await cartSub(dish, null);
      else if (act === "spec") openSpecPop(dish);
    });

    // 打开购物车
    document.getElementById("cart-open").addEventListener("click", async () => {
      if (!(state.cart || []).length) { Util.toast("购物车是空的"); return; }
      await refreshCartFromServer();
      showCartPop();
    });

    // 去结算
    document.getElementById("cart-go").addEventListener("click", () => {
      if (state.shopStatus !== 1) { Util.toast("店铺已打烊，暂不接受订单"); return; }
      if (!(state.cart || []).length) { Util.toast("请先添加商品"); return; }
      go("order");
    });

    // 搜索功能
    const searchInput = document.getElementById("menu-search-input");
    const searchClear = document.getElementById("search-clear");
    const searchResults = document.getElementById("menu-search-results");
    const searchBox = document.getElementById("menu-search");

    function doSearch(keyword) {
      if (!keyword || !keyword.trim()) {
        searchResults.classList.remove("show");
        searchBox.classList.remove("has-value");
        return;
      }
      searchBox.classList.add("has-value");
      const kw = keyword.trim().toLowerCase();
      const matched = Object.values(state.dishMap || {}).filter(d =>
        (d.name || "").toLowerCase().includes(kw) ||
        (d.description || "").toLowerCase().includes(kw)
      ).slice(0, 8);
      if (!matched.length) {
        searchResults.innerHTML = '<div class="sr-empty">未找到相关菜品</div>';
      } else {
        searchResults.innerHTML = matched.map(d =>
          '<div class="sr-item" data-key="' + dishKey(d) + '">'
          + '<img src="' + Util.img(d.image) + '" alt="' + Util.esc(d.name) + '">'
          + '<div class="sr-name">' + Util.esc(d.name) + '</div>'
          + '<div class="sr-price">￥' + Util.money(d.price) + '</div>'
          + "</div>"
        ).join("");
        searchResults.querySelectorAll(".sr-item").forEach(el => {
          el.addEventListener("click", () => {
            const dish = state.dishMap[el.dataset.key];
            if (!dish) return;
            searchResults.classList.remove("show");
            searchInput.value = "";
            searchBox.classList.remove("has-value");
            // 切换到该菜品所在分类并滚动到可视区域
            const catIdx = state.categories.findIndex(c => c.id === dish.categoryId);
            if (catIdx >= 0) {
              state.typeIndex = catIdx;
              document.querySelectorAll(".menu-types .type-item").forEach((x, i) => x.classList.toggle("active", i === catIdx));
              const list = dishesForType(catIdx);
              document.querySelector(".menu-dishes").innerHTML = list.length
                ? list.map(dd => dishHtml(withCartNumber(dd))).join("") + '<div style="height:96px;"></div>'
                : '<div class="empty-tip">该分类下暂无商品</div>';
              // 高亮目标菜品
              setTimeout(() => {
                const target = document.querySelector('.dish[data-key="' + dishKey(dish) + '"]');
                if (target) {
                  target.scrollIntoView({ behavior: "smooth", block: "center" });
                  target.style.boxShadow = "0 0 0 2px var(--brand-500)";
                  setTimeout(() => { target.style.boxShadow = ""; }, 1500);
                }
              }, 50);
            }
          });
        });
      }
      searchResults.classList.add("show");
    }

    let searchTimer = null;
    searchInput.addEventListener("input", () => {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(() => doSearch(searchInput.value), 220);
    });
    searchInput.addEventListener("focus", () => {
      if (searchInput.value.trim()) doSearch(searchInput.value);
    });
    searchClear.addEventListener("click", () => {
      searchInput.value = "";
      searchResults.classList.remove("show");
      searchBox.classList.remove("has-value");
      searchInput.focus();
    });
    // 点击页面其他区域关闭搜索结果
    document.getElementById("menu-root").addEventListener("click", e => {
      if (!e.target.closest("#menu-search")) {
        searchResults.classList.remove("show");
      }
    });
  }

  async function cartAdd(dish, flavor, silent) {
    if (state.shopStatus !== 1) { Util.toast("店铺已打烊，暂不接受点餐"); return; }
    try {
      const isSetmeal = !!dish._isSetmeal;
      const payload = isSetmeal ? { setmealId: dish.id } : { dishId: dish.id };
      if (flavor) payload.dishFlavor = flavor;
      await Api.cartAdd(payload);
      await refreshCartFromServer();
      if (!silent) Util.toast("已加入购物车");
    } catch (e) {
      Util.toast(e.message);
    }
  }

  async function cartSub(dish, flavor) {
    try {
      const isSetmeal = !!dish._isSetmeal;
      const payload = isSetmeal ? { setmealId: dish.id } : { dishId: dish.id };
      if (flavor) payload.dishFlavor = flavor;
      await Api.cartSub(payload);
      await refreshCartFromServer();
    } catch (e) {
      Util.toast(e.message);
    }
  }

  async function refreshCartFromServer() {
    try {
      state.cart = await Api.cartList() || [];
      updateCartUi();
    } catch (e) { /* 忽略 */ }
  }

  function updateCartUi() {
    const cart = state.cart || [];
    const total = cart.reduce((s, c) => s + c.amount * c.number, 0);
    const count = cart.reduce((s, c) => s + c.number, 0);
    const bar = document.querySelector(".cart-bar");
    if (bar) {
      const closed = state.shopStatus !== 1;
      bar.innerHTML = '<div class="cart-left" id="cart-open">'
        + '<span class="cart-ico">' + Ico.cart + '</span>'
        + (count > 0 ? '<span class="cart-num">' + count + "</span>" : "")
        + '<span class="cart-total"><small>￥</small>' + Util.money(total) + "</span>"
        + '</div><button class="cart-go' + (count === 0 || closed ? " disabled" : "") + '" id="cart-go">' + (closed ? "已打烊" : "去结算") + "</button>";
      document.getElementById("cart-go").addEventListener("click", () => {
        if (closed) { Util.toast("店铺已打烊，暂不接受订单"); return; }
        if (count > 0) go("order");
      });
      document.getElementById("cart-open").addEventListener("click", () => count > 0 && showCartPop());
    }
    // 菜品列表数量同步（重建当前分类列表的加号/数量）
    const listEl = document.querySelector(".menu-dishes");
    if (listEl) {
      const current = dishesForType(state.typeIndex);
      if (current.length) {
        const els = listEl.querySelectorAll(".dish");
        els.forEach(el => {
          const dish = state.dishMap[el.dataset.key];
          if (!dish) return;
          const slot = el.querySelector("[data-id='" + dish.id + "']");
          if (slot) slot.innerHTML = dishStepperHtml(withCartNumber(dish), dish._isSetmeal);
        });
      }
    }
    // 购物车弹层同步
    const pop = document.getElementById("cart-pop");
    if (pop && !pop.classList.contains("hidden")) showCartPop();
  }

  function showCartPop() {
    const pop = document.getElementById("cart-pop");
    const mask = document.getElementById("menu-mask");
    if (!pop) return;
    const cart = state.cart || [];
    pop.innerHTML = '<div class="cart-head"><div class="cart-title">购物车</div>'
      + '<button class="clear" id="cart-clear">' + Ico.trash + "清空</button></div>"
      + (cart.length ? cart.map((c, idx) =>
        '<div class="cart-item" data-idx="' + idx + '"><img src="' + Util.img(c.image) + '"><div class="ci-info">'
        + '<div class="ci-name">' + Util.esc(c.name) + "</div>"
        + '<div class="ci-flavor">' + Util.esc(c.dishFlavor || "") + "</div>"
        + '<div class="ci-price">￥' + Util.money(c.amount) + "</div></div>"
        + '<div class="stepper"><button class="step-sub" data-act="sub">−</button>'
        + '<span class="step-num">' + c.number + "</span>"
        + '<button class="step-add" data-act="add">+</button></div></div>'
      ).join("") : emptyTip("cart", "购物车是空的"))
      + '<div style="padding:10px 16px;text-align:right;font-size:12px;color:#999;">合计：<span style="color:var(--primary);font-size:16px;font-weight:700;">￥'
      + Util.money(cart.reduce((s, c) => s + c.amount * c.number, 0)) + "</span></div>";

    pop.classList.remove("hidden");
    mask.classList.remove("hidden");
    document.getElementById("cart-clear").addEventListener("click", async () => {
      if (!(await Util.confirm("确定清空购物车吗？"))) return;
      try {
        await Api.cartClean();
        await refreshCartFromServer();
        pop.classList.add("hidden");
        mask.classList.add("hidden");
      } catch (e) { Util.toast(e.message); }
    });
    pop.querySelectorAll("[data-act]").forEach(btn => {
      btn.addEventListener("click", async e => {
        e.stopPropagation();
        const idx = Number(btn.closest(".cart-item").dataset.idx);
        const item = state.cart[idx];
        if (!item) return;
        const dish = { id: item.dishId || item.setmealId, _isSetmeal: !!item.setmealId, flavors: [] };
        if (btn.dataset.act === "add") await cartAdd(dish, item.dishFlavor || null, true);
        else await cartSub(dish, item.dishFlavor || null);
        if (!(state.cart || []).length) { pop.classList.add("hidden"); mask.classList.add("hidden"); }
      });
    });
    mask.onclick = () => { pop.classList.add("hidden"); mask.classList.add("hidden"); };
  }

  function openSpecPop(dish) {
    const pop = document.getElementById("spec-pop");
    if (!pop) return;
    const flavors = dish.flavors || [];
    const selected = {};
    flavors.forEach(f => { selected[f.name] = (f.value || "").split(",")[0]; });

    function renderSpec() {
      pop.innerHTML = "<h3>" + Util.esc(dish.name) + "</h3>"
        + flavors.map(f =>
          '<div class="spec-group"><div class="sg-name">' + Util.esc(f.name) + "</div><div class=\"sg-options\">"
          + (f.value || "").split(",").map(v =>
            '<span class="sg-option' + (selected[f.name] === v ? " active" : "") + '" data-g="' + Util.esc(f.name) + '" data-v="' + Util.esc(v) + '">' + Util.esc(v) + "</span>"
          ).join("") + "</div></div>"
        ).join("")
        + '<div class="spec-foot"><div class="price">￥' + Util.money(dish.price) + '</div>'
        + '<button class="btn-primary" id="spec-add">加入购物车</button></div>';

      pop.querySelectorAll(".sg-option").forEach(el => {
        el.addEventListener("click", () => {
          selected[el.dataset.g] = el.dataset.v;
          renderSpec();
        });
      });
      document.getElementById("spec-add").addEventListener("click", async () => {
        const flavor = Object.values(selected).join(",");
        await cartAdd(dish, flavor);
        pop.classList.add("hidden");
        document.getElementById("menu-mask").classList.add("hidden");
      });
    }
    pop.classList.remove("hidden");
    document.getElementById("menu-mask").classList.remove("hidden");
    document.getElementById("menu-mask").onclick = () => {
      pop.classList.add("hidden");
      document.getElementById("menu-mask").classList.add("hidden");
    };
    renderSpec();
  }

  // ==================== 下单页 ====================
  async function pageOrder() {
    if (!guard()) return;
    render(navbar("确认订单", { back: true }) + '<div id="order-root" class="page-pad"></div>', { title: "确认订单" });
    const root = document.getElementById("order-root");
    try {
      const cart = await Api.cartList();
      if (!cart || !cart.length) { root.innerHTML = emptyTip("cart", "购物车是空的", '<button class="btn-primary" onclick="App.go(\'menu\')">去点餐</button>'); return; }
      state.cart = cart;
      const addresses = await Api.addressList();
      let addr = (addresses || []).find(a => a.isDefault === 1) || (addresses || [])[0] || null;
      root.innerHTML = orderHtml(cart, addr, addresses || []);
      bindOrderEvents(addr, addresses || []);
    } catch (e) {
      root.innerHTML = emptyTip("alert", e.message);
    }
  }

  function orderHtml(cart, addr, addresses) {
    const total = cart.reduce((s, c) => s + c.amount * c.number, 0);
    const remarks = ["不要辣", "少辣", "多辣", "不要香菜", "多加米饭", "尽快送达"];
    return '<div class="card" id="addr-card">'
      + '<div class="card-title">配送地址<span class="link" id="addr-change">切换</span></div>'
      + (addr
        ? '<div class="addr-item"><div class="ai-radio on">' + Ico.pin + '</div><div class="ai-main">'
          + '<div class="ai-name">' + Util.esc(addr.consignee || "") + '<span>' + Util.esc(addr.phone || "") + "</span></div>"
          + '<div class="ai-detail">' + Util.esc((addr.provinceName || "") + (addr.cityName || "") + (addr.districtName || "") + (addr.detail || "")) + "</div>"
          + '</div></div>'
        : '<div class="addr-item" style="color:#999;">还没有收货地址，请先添加 <span style="color:var(--primary);">去添加 ›</span></div>')
      + "</div>"
      + '<div class="card"><div class="card-title">商品清单</div>'
      + cart.map(c =>
        '<div class="order-goods"><img src="' + Util.img(c.image) + '"><div><div class="og-name">' + Util.esc(c.name) + "</div>"
        + (c.dishFlavor ? '<div class="og-flavor">' + Util.esc(c.dishFlavor) + "</div>" : "")
        + '</div><div class="og-right"><div class="og-price">￥' + Util.money(c.amount) + "</div>"
        + '<div style="font-size:11px;color:#999;">x' + c.number + "</div></div></div>"
      ).join("")
      + "</div>"
      + '<div class="card"><div class="card-title">备注</div>'
      + '<input id="order-remark" placeholder="口味、偏好等要求（可留空）">'
      + '<div class="remark-row">' + remarks.map(r => '<button class="rk-item" data-r="' + r + '">' + r + "</button>").join("") + "</div></div>"
      + '<div class="card"><div class="card-title">订单信息</div><div class="order-summary">'
      + '<div class="os-row"><span>配送费</span><span>￥6.00</span></div>'
      + '<div class="os-row"><span>打包费</span><span>￥1.00</span></div>'
      + '<div class="os-row total"><span>应付金额</span><span>￥' + Util.money(total + 7) + "</span></div>"
      + "</div></div>"
      + '<div class="bottom-bar"><div class="bb-price"><small>￥</small>' + Util.money(total + 7) + '</div>'
      + '<button class="btn-primary" id="order-submit">提交订单</button></div>';
  }

  function bindOrderEvents(defaultAddr, addresses) {
    let addr = defaultAddr;
    document.querySelectorAll(".rk-item").forEach(el => {
      el.addEventListener("click", () => {
        document.querySelectorAll(".rk-item").forEach(x => x.classList.remove("active"));
        el.classList.add("active");
        document.getElementById("order-remark").value = el.dataset.r;
      });
    });

    document.getElementById("addr-change").addEventListener("click", () => {
      const shell = document.querySelector(".page-shell");
      let picker = document.getElementById("addr-picker-pop");
      if (picker) { picker.remove(); return; }
      picker = document.createElement("div");
      picker.id = "addr-picker-pop";
      picker.innerHTML = '<div class="pop-mask" id="addr-mask"></div>'
        + '<div class="addr-sheet">'
        + '<div class="cart-head"><div>选择收货地址</div><button class="link" id="addr-add" style="color:var(--primary);font-size:13px;">＋ 新增地址</button></div>'
        + '<div class="addr-sheet-list">'
        + (addresses.length ? addresses.map(a =>
          '<div class="addr-item" data-id="' + a.id + '"><div class="ai-radio' + (addr && addr.id === a.id ? " on" : "") + '">●</div><div class="ai-main">'
          + '<div class="ai-name">' + Util.esc(a.consignee || "") + "<span>" + Util.esc(a.phone || "") + "</span></div>"
          + '<div class="ai-detail">' + Util.esc((a.provinceName || "") + (a.cityName || "") + (a.districtName || "") + (a.detail || "")) + "</div>"
          + "</div></div>"
        ).join("") : '<div class="empty-tip">暂无地址</div>')
        + "</div></div>";
      shell.appendChild(picker);
      picker.querySelectorAll(".addr-item").forEach(el => {
        el.addEventListener("click", async () => {
          addr = addresses.find(a => a.id === Number(el.dataset.id));
          await Api.addressDefault(addr.id);
          go("order");
        });
      });
      document.getElementById("addr-add").addEventListener("click", () => go("addressForm", { from: "order" }));
      document.getElementById("addr-mask").addEventListener("click", () => picker.remove());
    });

    document.querySelector("#addr-card").addEventListener("click", e => {
      if (e.target.id === "addr-change") return;
      if (!addr) go("addressForm", { from: "order" });
    });

    document.getElementById("order-submit").addEventListener("click", async () => {
      if (!addr) { Util.toast("请先选择收货地址"); return; }
      const total = (state.cart || []).reduce((s, c) => s + c.amount * c.number, 0);
      const payload = {
        addressBookId: addr.id,
        payMethod: 1,
        remark: document.getElementById("order-remark").value || "",
        estimatedDeliveryTime: null,
        deliveryStatus: 1,
        tablewareNumber: 1,
        tablewareStatus: 1,
        packAmount: 1,
        amount: Math.round((total + 7) * 100) / 100
      };
      try {
        const res = await Api.orderSubmit(payload);
        state.currentOrder = res;
        go("pay", { orderId: res.id });
      } catch (e) {
        Util.toast(e.message);
      }
    });
  }

  // ==================== 支付页 ====================
  async function pagePay() {
    if (!guard()) return;
    const orderId = state.params.orderId;
    let order = state.currentOrder;
    if (!order && orderId) {
      try { order = await Api.orderDetail(orderId); } catch (e) { /* 忽略 */ }
    }
    if (!order) { Util.toast("订单不存在"); go("history"); return; }

    render(navbar("收银台", { back: true }) + '<div class="pay-page">'
      + '<div class="pay-box"><div class="pb-time" id="pay-time">支付剩余时间 15:00</div>'
      + '<div class="pb-money"><small>￥</small>' + Util.money(order.orderAmount || order.amount) + "</div>"
      + '<div class="pb-name">苍穹餐厅 - ' + Util.esc(order.orderNumber || "") + "</div></div>"
      + '<div class="card pay-methods">'
      + '<div class="pm-item"><span class="pm-ico">💳</span><span class="pm-name">模拟支付（点击即付款）</span><span class="pm-radio on"></span></div>'
      + '<div style="font-size:11px;color:#aaa;padding:4px 2px;">个人练习项目，不接入真实支付渠道</div>'
      + "</div>"
      + '<button class="btn-primary" id="pay-btn">确认支付</button>'
      + "</div>", { title: "收银台" });

    let remain = 15 * 60;
    const timer = setInterval(() => {
      remain--;
      const el = document.getElementById("pay-time");
      if (!el) { clearInterval(timer); return; }
      if (remain <= 0) {
        clearInterval(timer);
        el.textContent = "订单已超时";
      } else {
        const m = String(Math.floor(remain / 60)).padStart(2, "0");
        const s = String(remain % 60).padStart(2, "0");
        el.textContent = "支付剩余时间 " + m + ":" + s;
      }
    }, 1000);

    document.getElementById("pay-btn").addEventListener("click", async () => {
      try {
        await Api.orderPay({ orderNumber: order.orderNumber, payMethod: 1 });
        clearInterval(timer);
        go("success", { orderId: order.id, number: order.orderNumber, amount: order.orderAmount || order.amount });
      } catch (e) {
        Util.toast(e.message);
      }
    });
  }

  // ==================== 支付成功 ====================
  function pageSuccess() {
    if (!guard()) return;
    const p = state.params;
    render('<div class="success-page">'
      + '<div class="su-ico">✓</div>'
      + "<h2>支付成功</h2>"
      + "<p>订单号：" + Util.esc(p.number || "") + "<br>已通知商家备餐，请耐心等待</p>"
      + '<button class="btn-primary" id="su-detail">查看订单详情</button>'
      + '<button class="btn-plain" id="su-menu">返回首页</button>'
      + "</div>", { title: "支付成功" });
    document.getElementById("su-detail").addEventListener("click", () => go("history"));
    document.getElementById("su-menu").addEventListener("click", () => go("menu"));
  }

  // ==================== 历史订单 ====================
  async function pageHistory() {
    if (!guard()) return;
    render('<div class="order-top"><button class="pt-back" onclick="history.back()">‹</button><span>我的订单</span></div>'
      + '<div id="his-status" class="order-tabs"></div><div id="his-root" class="page-pad"></div>'
      , { title: "我的订单" });
    loadHistory("");
  }

  async function loadHistory(status) {
    const tabs = [{ v: "", t: "全部" }, { v: 1, t: "待付款" }, { v: 2, t: "待接单" }, { v: 3, t: "已接单" }, { v: 4, t: "派送中" }, { v: 5, t: "已完成" }, { v: 6, t: "已取消" }];
    document.getElementById("his-status").innerHTML = tabs.map(t =>
      '<span class="st-item' + (String(status) === String(t.v) ? " active" : "") + '" data-v="' + t.v + '">' + t.t + "</span>"
    ).join("");
    document.querySelectorAll(".st-item").forEach(el => {
      el.addEventListener("click", () => loadHistory(el.dataset.v === "" ? "" : Number(el.dataset.v)));
    });
    const root = document.getElementById("his-root");
    root.innerHTML = '<div class="empty-tip">加载中…</div>';
    try {
      const res = await Api.orderHistory(1, 50, status);
      const list = res.records || [];
      root.innerHTML = list.length ? list.map(orderCardHtml).join("") : emptyTip("receipt", "暂无订单", '<button class="btn-primary" onclick="App.go(\'menu\')">去点餐</button>');
      bindOrderCards(list);
    } catch (e) {
      root.innerHTML = emptyTip("alert", e.message);
    }
  }

  function orderCardHtml(o) {
    const items = o.orderDetailList || [];
    return '<div class="order-card oc-s' + o.status + '">'
      + '<div class="oc-head"><span>' + Util.esc(o.number || "") + "</span><span class=\"oc-status\">" + Util.orderStatusText(o.status) + "</span></div>"
      + '<div class="oc-goods">' + (items.length ? items.map(it =>
        '<div class="oc-row"><img src="' + Util.img(it.image) + '"><span class="oc-name">' + Util.esc(it.name)
        + (it.dishFlavor ? '<small style="color:#bbb;">（' + Util.esc(it.dishFlavor) + "）</small>" : "") + "</span>"
        + '<span class="oc-num">x' + it.number + "</span></div>"
      ).join("") : '<div class="oc-row">' + Util.esc(o.orderDishes || "订单明细") + "</div>")
      + "</div>"
      + '<div class="oc-foot"><div class="oc-amount">共' + (items.reduce((s, i) => s + (i.number || 0), 0) || 1) + "件，实付 <b>￥" + Util.money(o.amount) + "</b></div>"
      + '<div class="oc-actions" data-id="' + o.id + '" data-number="' + Util.esc(o.number || "") + '">' + orderActions(o.status)
      + '<button class="btn-plain" data-act="consult">咨询订单</button>'
      + "</div></div></div>";
  }

  function orderActions(status) {
    if (status === 1) {
      return '<button class="btn-primary" data-act="pay">去支付</button><button class="btn-plain" data-act="cancel">取消</button>';
    }
    if (status === 2) {
      return '<button class="btn-plain" data-act="remind">催单</button><button class="btn-plain" data-act="cancel">取消订单</button>';
    }
    if (status === 3 || status === 4) {
      return '<button class="btn-plain" data-act="remind">催单</button>';
    }
    if (status === 5 || status === 6) {
      return '<button class="btn-plain" data-act="repetition">再来一单</button>';
    }
    return "";
  }

  function bindOrderCards(list) {
    document.querySelectorAll(".oc-actions").forEach(el => {
      el.addEventListener("click", async e => {
        const btn = e.target.closest("[data-act]");
        if (!btn) return;
        const id = Number(el.dataset.id);
        const number = el.dataset.number;
        const act = btn.dataset.act;
        try {
          if (act === "pay") {
            const order = list.find(o => o.id === id);
            state.currentOrder = order;
            go("pay", { orderId: id });
          } else if (act === "cancel") {
            if (!(await Util.confirm("确定取消该订单吗？"))) return;
            await Api.orderCancel(id);
            Util.toast("订单已取消");
            loadHistory(parseHash().params.status || "");
          } else if (act === "remind") {
            await Api.orderReminder(id);
            Util.toast("已提醒商家");
          } else if (act === "repetition") {
            await Api.orderRepetition(id);
            Util.toast("已加入购物车");
          } else if (act === "consult") {
            go("chat", { orderId: id });
          }
        } catch (err) {
          Util.toast(err.message);
        }
      });
    });
  }

  // ==================== 地址簿 ====================
  async function pageAddress() {
    if (!guard()) return;
    render(navbar("收货地址", { back: true, right: "＋ 新增", rightAction: "onclick=\"location.hash='#/addressForm'\"" })
      + '<div id="addr-root" class="page-pad"></div>', { title: "收货地址" });
    const root = document.getElementById("addr-root");
    try {
      const list = await Api.addressList();
      root.innerHTML = list.length ? list.map(a =>
        '<div class="card addr-item"><div class="ai-main">'
        + '<div class="ai-name">' + Util.esc(a.consignee || "") + '<span>' + Util.esc(a.label || "") + "</span>"
        + '<span class="ai-phone">' + Util.esc(a.phone || "") + "</span></div>"
        + '<div class="ai-detail">' + Util.esc((a.provinceName || "") + (a.cityName || "") + (a.districtName || "") + (a.detail || "")) + "</div>"
        + '<div class="ai-actions"><span data-act="edit" data-id="' + a.id + '">' + Ico.pencil + "编辑</span>"
        + '<span data-act="del" data-id="' + a.id + '">' + Ico.trash + "删除</span>"
        + (a.isDefault === 1 ? '<span style="color:var(--primary);">默认</span>' : '<span data-act="default" data-id="' + a.id + '">设为默认</span>')
        + "</div></div></div>"
      ).join("") : emptyTip("pin", "暂无收货地址，点右上角新增", '<button class="btn-primary" onclick="App.go(\'addressForm\')">新增地址</button>');
      root.querySelectorAll("[data-act]").forEach(el => {
        el.addEventListener("click", async () => {
          const id = Number(el.dataset.id);
          const act = el.dataset.act;
          if (act === "edit") go("addressForm", { id });
          else if (act === "del") {
            if (!(await Util.confirm("确定删除该地址吗？"))) return;
            try { await Api.addressDelete(id); pageAddress(); } catch (e) { Util.toast(e.message); }
          } else if (act === "default") {
            try { await Api.addressDefault(id); pageAddress(); } catch (e) { Util.toast(e.message); }
          }
        });
      });
    } catch (e) {
      root.innerHTML = emptyTip("alert", e.message);
    }
  }

  async function pageAddressForm() {
    if (!guard()) return;
    const id = state.params.id;
    let addr = { consignee: "", phone: "", provinceName: "北京市", cityName: "北京市", districtName: "朝阳区", detail: "", label: "家", isDefault: 0 };
    if (id) {
      try {
        const list = await Api.addressList();
        addr = (list || []).find(a => a.id === Number(id)) || addr;
      } catch (e) { /* 忽略 */ }
    }
    const from = state.params.from;
    render(navbar(id ? "编辑地址" : "新增地址", { back: true }) + '<div class="page-pad addr-form">'
      + '<div class="card">'
      + '<div class="field"><label>收货人</label><input id="af-consignee" value="' + Util.esc(addr.consignee || "") + '" placeholder="姓名"></div>'
      + '<div class="field"><label>手机号</label><input id="af-phone" value="' + Util.esc(addr.phone || "") + '" placeholder="11位手机号"></div>'
      + '<div class="field"><label>所在地区</label><div class="field-row">'
      + '<select id="af-province"><option>北京市</option><option>上海市</option><option>广东省</option><option>浙江省</option></select>'
      + '<select id="af-city"><option>北京市</option><option>上海市</option><option>广州市</option><option>杭州市</option></select>'
      + '<select id="af-district"><option>朝阳区</option><option>海淀区</option><option>浦东新区</option><option>天河区</option></select>'
      + "</div></div>"
      + '<div class="field"><label>详细地址</label><textarea id="af-detail" rows="2" placeholder="街道、楼栋、门牌号">' + Util.esc(addr.detail || "") + "</textarea></div>"
      + '<div class="field"><label>标签</label><div class="field-row">'
      + ["家", "公司", "学校"].map(l =>
        '<button class="rk-item' + ((addr.label || "家") === l ? " active" : "") + '" data-label="' + l + '">' + l + "</button>"
      ).join("") + "</div></div>"
      + '<div class="default-row"><span>设为默认地址</span><span class="switch' + (addr.isDefault === 1 ? " on" : "") + '" id="af-default"></span></div>'
      + "</div>"
      + '<button class="btn-primary" id="af-save">保存地址</button>'
      + "</div>", { title: "地址" });

    let isDefault = addr.isDefault === 1;
    let label = addr.label || "家";
    document.getElementById("af-default").addEventListener("click", function () {
      isDefault = !isDefault;
      this.classList.toggle("on", isDefault);
    });
    document.querySelectorAll(".rk-item").forEach(el => {
      el.addEventListener("click", () => {
        document.querySelectorAll(".rk-item").forEach(x => x.classList.remove("active"));
        el.classList.add("active");
        label = el.dataset.label;
      });
    });
    document.getElementById("af-save").addEventListener("click", async () => {
      const payload = {
        id: id ? Number(id) : undefined,
        consignee: document.getElementById("af-consignee").value.trim(),
        phone: document.getElementById("af-phone").value.trim(),
        provinceName: document.getElementById("af-province").value,
        cityName: document.getElementById("af-city").value,
        districtName: document.getElementById("af-district").value,
        detail: document.getElementById("af-detail").value.trim(),
        label,
        isDefault: isDefault ? 1 : 0
      };
      if (!payload.consignee || !payload.phone) { Util.toast("请填写收货人和手机号"); return; }
      try {
        if (id) await Api.addressUpdate(payload);
        else await Api.addressSave(payload);
        Util.toast("保存成功");
        if (from === "order") go("order");
        else go("address");
      } catch (e) {
        Util.toast(e.message);
      }
    });
  }

  // ==================== 消息会话 / 聊天 ====================
  let chatWs = null;
  let chatReconnectTimer = null;
  let chatReadTimer = null;

  function chatWsUrl() {
    const proto = location.protocol === "https:" ? "wss" : "ws";
    return proto + "://" + location.host + "/ws/chat?type=1&token=" + encodeURIComponent(Api.getToken());
  }

  function ensureChatSocket() {
    if (!Api.getToken()) return;
    if (chatWs && (chatWs.readyState === WebSocket.OPEN || chatWs.readyState === WebSocket.CONNECTING)) return;
    if (chatReconnectTimer) { clearTimeout(chatReconnectTimer); chatReconnectTimer = null; }
    let ws;
    try { ws = new WebSocket(chatWsUrl()); } catch (e) { return; }
    chatWs = ws;
    ws.onopen = () => {};
    ws.onmessage = ev => {
      let env;
      try { env = JSON.parse(ev.data); } catch (e) { return; }
      if (env.type === "msg" && env.message) {
        const m = env.message;
        if (m.senderType === 2 || m.senderType === 3) {
          //商家/机器人发来的消息
          if (state.route === "chat") {
            appendBubble(m);
            scheduleMarkRead();
          } else {
            refreshUnread();
            if (state.route === "messages") loadConversations();
          }
        } else if (state.route === "messages") {
          //多标签页自己发的消息，刷新会话列表
          loadConversations();
        }
      } else if (env.type === "modeSwitch") {
        //后端转人工时要求前端切到人工模式
        localStorage.setItem("chatMode", env.mode === "human" ? "human" : "bot");
        syncChatModeUI();
      } else if (env.type === "ack" && state.route === "chat") {
        resolveAckBubble(env.clientMsgId, env.message);
      } else if (env.type === "error") {
        Util.toast(env.msg || "消息发送失败");
      }
    };
    ws.onclose = () => {
      if (chatWs !== ws) return;
      if (Api.getToken() && state.route !== "login" && state.route !== "register") {
        chatReconnectTimer = setTimeout(ensureChatSocket, 3000);
      }
    };
    ws.onerror = () => { try { ws.close(); } catch (e) {} };
  }

  function closeChatSocket() {
    if (chatReconnectTimer) { clearTimeout(chatReconnectTimer); chatReconnectTimer = null; }
    if (chatWs) {
      const ws = chatWs;
      chatWs = null;
      ws.onclose = null;
      ws.onerror = null;
      ws.onmessage = null;
      try { ws.close(); } catch (e) {}
    }
    if (chatReadTimer) { clearTimeout(chatReadTimer); chatReadTimer = null; }
  }

  function scheduleMarkRead() {
    if (chatReadTimer) clearTimeout(chatReadTimer);
    chatReadTimer = setTimeout(async () => {
      try { await Api.chatRead(); refreshUnread(); } catch (e) {}
    }, 400);
  }

  async function pageMessages() {
    if (!guard()) return;
    render('<div class="order-top"><button class="pt-back" onclick="history.back()">‹</button><span>消息</span></div>'
      + '<div id="conv-root" class="page-pad"><div class="empty-tip">加载中…</div></div>'
      , { title: "消息" });
    await loadConversations();
  }

  async function loadConversations() {
    const root = document.getElementById("conv-root");
    if (!root) return;
    try {
      const list = await Api.chatConversations();
      const c = (list && list[0]) || null;
      const hasMsg = c && c.lastTime;
      let preview = "有问题随时联系商家客服";
      if (hasMsg) {
        preview = c.lastMsgType === 2 ? "[订单卡片]" : Util.esc(c.lastContent || "");
      }
      root.innerHTML = '<div class="conv-item" onclick="App.go(\'chat\')">'
        + '<div class="conv-avatar">' + Ico.store + '</div>'
        + '<div class="conv-main"><div class="conv-line1"><span class="conv-name">商家客服</span>'
        + '<span class="conv-time">' + (hasMsg ? fmtMsgTime(c.lastTime) : "") + "</span></div>"
        + '<div class="conv-line2"><span class="conv-last">' + preview + "</span>"
        + (c && c.unreadCount ? '<span class="conv-badge">' + (c.unreadCount > 99 ? "99+" : c.unreadCount) + "</span>" : "")
        + "</div></div></div>";
    } catch (e) {
      root.innerHTML = emptyTip("alert", e.message || "加载失败", '<button class="btn-primary" onclick="App.go(\'messages\')">重新加载</button>');
    }
  }

  function fmtMsgTime(t) {
    if (!t) return "";
    const s = String(t);
    const d = new Date(s.replace(/-/g, "/"));
    if (isNaN(d.getTime())) return s.length > 16 ? s.slice(5, 16) : s;
    const now = new Date();
    const sameDay = d.toDateString() === now.toDateString();
    const pad = n => String(n).padStart(2, "0");
    if (sameDay) return pad(d.getHours()) + ":" + pad(d.getMinutes());
    return (d.getMonth() + 1) + "月" + d.getDate() + "日";
  }

  //客服模式：bot=智能客服优先 human=直接人工（记住用户选择）
  function getChatMode() { return localStorage.getItem("chatMode") === "human" ? "human" : "bot"; }

  //同步聊天页顶栏模式按钮/标题（不在聊天页时静默跳过）
  function syncChatModeUI() {
    const modeBtn = document.getElementById("chat-mode");
    const titleEl = document.getElementById("chat-title");
    const isBot = getChatMode() === "bot";
    if (modeBtn) {
      modeBtn.innerHTML = isBot ? (Ico.bot + "智能客服") : (Ico.headset + "人工客服");
      modeBtn.classList.toggle("bot", isBot);
      modeBtn.classList.toggle("human", !isBot);
    }
    if (titleEl) titleEl.textContent = isBot ? "商家客服 · 智能" : "商家客服 · 人工";
  }

  async function pageChat(params) {
    if (!guard()) return;
    render('<div class="chat-page">'
      + '<div class="chat-top"><button class="pt-back" onclick="history.back()">‹</button>'
      + '<span class="chat-title" id="chat-title"></span>'
      + '<button class="chat-mode-btn" id="chat-mode"></button>'
      + '<button class="chat-pick-btn" id="chat-pick">' + Ico.receipt + "订单</button></div>"
      + (params.orderId ? '<div id="chat-banner" class="chat-banner"></div>' : "")
      + '<div id="chat-msgs" class="chat-msgs"></div>'
      + '<div class="chat-quick" id="chat-quick">'
      + '<button type="button" data-q="帮我下单">帮我下单</button>'
      + '<button type="button" data-q="宫保鸡丁2份">来份招牌</button>'
      + '<button type="button" data-q="转人工">转人工</button></div>'
      + '<div class="chat-input-bar"><input id="chat-input" placeholder="请输入消息…" maxlength="500">'
      + '<button id="chat-send" class="btn-primary">发送</button></div>'
      + '<div id="chat-mask" class="mask-layer hidden"></div>'
      + '<div id="order-picker" class="order-picker hidden"></div>'
      + "</div>", { title: "商家客服" });

    const msgs = document.getElementById("chat-msgs");
    msgs.innerHTML = '<div class="empty-tip">加载中…</div>';
    let history = [];
    try {
      history = await Api.chatHistory();
      await Api.chatRead();
      await refreshUnread();
    } catch (e) {
      msgs.innerHTML = emptyTip("alert", e.message || "加载失败", '<button class="btn-primary" onclick="App.go(\'chat\')">重新加载</button>');
      return;
    }
    msgs.innerHTML = history.length
      ? history.map(bubbleHtml).join("")
      : '<div class="chat-tip">还没有消息，发送一条开始咨询吧<br>可以问我退款、配送问题，也可以直接说「帮我下单：宫保鸡丁2份」</div>';
    scrollChatBottom();

    const input = document.getElementById("chat-input");
    const sendBtn = document.getElementById("chat-send");
    const modeBtn = document.getElementById("chat-mode");

    syncChatModeUI();
    modeBtn.addEventListener("click", () => {
      localStorage.setItem("chatMode", getChatMode() === "bot" ? "human" : "bot");
      syncChatModeUI();
    });

    const doSend = () => {
      const text = input.value.trim();
      if (!text) return;
      if (!chatWs || chatWs.readyState !== WebSocket.OPEN) {
        Util.toast("连接中，请稍后再试");
        return;
      }
      const clientMsgId = "t" + Date.now() + Math.floor(Math.random() * 1000);
      const temp = { clientMsgId, senderType: 1, msgType: 1, content: text, createTime: null, sending: true };
      appendBubble(temp);
      const payload = { clientMsgId, msgType: 1, content: text };
      if (getChatMode() === "human") payload.forceHuman = true;
      chatWs.send(JSON.stringify(payload));
      input.value = "";
    };
    sendBtn.addEventListener("click", doSend);
    input.addEventListener("keydown", e => { if (e.key === "Enter") doSend(); });

    document.getElementById("chat-quick").addEventListener("click", e => {
      const q = e.target.dataset.q;
      if (!q) return;
      if (!chatWs || chatWs.readyState !== WebSocket.OPEN) {
        Util.toast("连接中，请稍后再试");
        return;
      }
      input.value = q;
      doSend();
    });

    document.getElementById("chat-pick").addEventListener("click", openOrderPicker);
    document.getElementById("chat-mask").addEventListener("click", closeOrderPicker);

    if (params.orderId) {
      const banner = document.getElementById("chat-banner");
      try {
        const o = await Api.orderDetail(params.orderId);
        banner.innerHTML = '<span>咨询订单：' + Util.esc(o.number) + "　￥" + Util.money(o.amount) + "</span>"
          + '<button id="banner-send" class="btn-primary">发送订单</button>';
        document.getElementById("banner-send").addEventListener("click", () => sendOrderCard(o));
      } catch (e) { banner.remove(); }
    }
  }

  function scrollChatBottom() {
    const msgs = document.getElementById("chat-msgs");
    if (msgs) msgs.scrollTop = msgs.scrollHeight;
  }

  function bubbleHtml(m) {
    const mine = m.senderType === 1;
    const isBot = m.senderType === 3;
    const cmid = m.clientMsgId ? ' data-cmid="' + Util.esc(m.clientMsgId) + '"' : "";
    let body;
    if (m.msgType === 2) {
      let snap = {};
      try { snap = JSON.parse(m.content || "{}"); } catch (e) {}
      body = (isBot ? '<div class="bot-tag">智能客服</div>' : "")
        + '<div class="chat-order-card' + (isBot ? " bot-order" : "") + '" onclick="App.go(\'order\',{orderId:' + (snap.orderId || 0) + '})">'
        + '<div class="coc-head">' + Ico.receipt + "订单卡片" + (isBot ? "·代下单" : "") + (m.sending ? "<small>（发送中）</small>" : "") + "</div>"
        + '<div class="coc-no">' + Util.esc(snap.number || "") + "</div>"
        + '<div class="coc-foot"><span>' + Util.orderStatusText(snap.status) + "</span><b>￥" + Util.money(snap.amount) + "</b></div></div>";
    } else {
      body = (isBot ? '<div class="bot-tag">智能客服</div>' : "")
        + '<div class="bubble' + (m.sending ? " sending" : "") + (isBot ? " bot" : "") + '">' + Util.esc(m.content || "") + "</div>";
    }
    const time = m.createTime ? '<div class="msg-time">' + Util.esc(String(m.createTime).slice(5, 16)) + "</div>" : "";
    const myAvatarUrl = mine ? Api.getAvatar() : "";
    const rightAvatar = myAvatarUrl
      ? '<div class="msg-avatar my-msg-avatar"><img src="' + myAvatarUrl + '" alt="我的头像"></div>'
      : "";
    return '<div class="msg-row ' + (mine ? "mine" : "other") + '"' + cmid + " data-id='" + (m.id || "") + "'>"
      + (mine ? "" : '<div class="msg-avatar ' + (isBot ? "av-bot" : "av-store") + '">' + (isBot ? Ico.bot : Ico.store) + "</div>")
      + '<div class="msg-col">' + time + body + "</div>" + rightAvatar + "</div>";
  }

  function appendBubble(m) {
    const msgs = document.getElementById("chat-msgs");
    if (!msgs) return;
    const tip = msgs.querySelector(".chat-tip");
    if (tip) tip.remove();
    msgs.insertAdjacentHTML("beforeend", bubbleHtml(m));
    scrollChatBottom();
  }

  function resolveAckBubble(clientMsgId, saved) {
    if (!clientMsgId) return;
    const node = document.querySelector('[data-cmid="' + clientMsgId + '"]');
    if (!node) return;
    node.outerHTML = bubbleHtml(saved);
    scrollChatBottom();
  }

  function sendOrderCard(o) {
    if (!chatWs || chatWs.readyState !== WebSocket.OPEN) {
      Util.toast("连接中，请稍后再试");
      return;
    }
    const clientMsgId = "t" + Date.now() + Math.floor(Math.random() * 1000);
    const temp = {
      clientMsgId, senderType: 1, msgType: 2, sending: true,
      content: JSON.stringify({ orderId: o.id, number: o.number, amount: o.amount, status: o.status })
    };
    appendBubble(temp);
    chatWs.send(JSON.stringify({ clientMsgId, msgType: 2, orderId: o.id }));
    const banner = document.getElementById("chat-banner");
    if (banner) banner.remove();
  }

  async function openOrderPicker() {
    const mask = document.getElementById("chat-mask");
    const pop = document.getElementById("order-picker");
    mask.classList.remove("hidden");
    pop.classList.remove("hidden");
    pop.innerHTML = '<div class="op-title">选择要咨询的订单</div><div id="op-list" class="op-list"><div class="empty-tip">加载中…</div></div>';
    try {
      const res = await Api.orderHistory(1, 20, "");
      const list = res.records || [];
      const box = document.getElementById("op-list");
      box.innerHTML = list.length ? list.map(o =>
        '<div class="op-item" data-id="' + o.id + '"><div><div class="op-no">' + Util.esc(o.number) + "</div>"
        + '<div class="op-status">' + Util.orderStatusText(o.status) + "</div></div>"
        + '<b>￥' + Util.money(o.amount) + "</b></div>"
      ).join("") : emptyTip("receipt", "暂无订单");
      box.querySelectorAll(".op-item").forEach(el => {
        el.addEventListener("click", () => {
          const o = list.find(x => x.id === Number(el.dataset.id));
          closeOrderPicker();
          sendOrderCard(o);
        });
      });
    } catch (e) {
      document.getElementById("op-list").innerHTML = emptyTip("alert", e.message || "加载失败");
    }
  }

  function closeOrderPicker() {
    document.getElementById("chat-mask").classList.add("hidden");
    document.getElementById("order-picker").classList.add("hidden");
  }

  // ==================== 我的 ====================
  async function pageMy() {
    if (!guard()) return;
    const user = Api.getUser();
    // 统计数据（任一失败不影响页面渲染）
    let orderTotal = 0, addrCount = 0, cartCount = 0;
    try { orderTotal = (await Api.orderHistory(1, 1, "")).total || 0; } catch (e) {}
    try { addrCount = (await Api.addressList() || []).length; } catch (e) {}
    try { cartCount = (await Api.cartList() || []).length; } catch (e) {}

    render('<div class="my-header">'
      + '<div class="mh-row"><div class="my-avatar">' + avatarInner(user) + "</div>"
      + "<div><div class=\"my-name\">" + Util.esc(user.name || user.username || "用户") + "</div>"
      + '<div class="my-role">' + Util.esc(user.username || "") + " · 用户账号</div></div></div>"
      + '<div class="my-stats">'
      + '<div class="ms-item" onclick="App.go(\'history\')"><div class="ms-num">' + orderTotal + '</div><div class="ms-label">订单</div></div>'
      + '<div class="ms-item" onclick="App.go(\'address\')"><div class="ms-num">' + addrCount + '</div><div class="ms-label">地址</div></div>'
      + '<div class="ms-item" onclick="App.go(\'menu\')"><div class="ms-num">' + cartCount + '</div><div class="ms-label">点餐</div></div>'
      + "</div></div>"
      + '<div class="my-group">'
      + '<div class="my-item" onclick="App.go(\'profile\')"><span class="mi-ico">' + Ico.userEdit + '</span>编辑资料<span class="mi-arrow">›</span></div>'
      + '<div class="my-item" onclick="App.go(\'history\')"><span class="mi-ico">' + Ico.receipt + '</span>我的订单<span class="mi-arrow">›</span></div>'
      + '<div class="my-item" onclick="App.go(\'address\')"><span class="mi-ico">' + Ico.pin + '</span>收货地址<span class="mi-arrow">›</span></div>'
      + "</div>"
      + '<div class="my-group">'
      + '<div class="my-item" onclick="App.logout()"><span class="mi-ico mi-logout">' + Ico.logout + '</span>退出登录<span class="mi-arrow">›</span></div>'
      + "</div>", { title: "我的" });
  }

  // ==================== 编辑资料 ====================
  async function pageProfile() {
    if (!guard()) return;
    render('<div class="empty-tip">加载中…</div>', { title: "编辑资料" });
    let user;
    try {
      user = await Api.userInfo();
      // 刷新本地缓存
      Api.setUser(Object.assign(Api.getUser() || {}, user));
    } catch (e) {
      render('<div class="empty-tip">获取资料失败：' + Util.esc(e.message || e) + '</div><div style="text-align:center;padding:20px;"><button class="btn-primary" onclick="App.go(\'profile\')">重试</button></div>', { title: "编辑资料" });
      return;
    }

    const avatarChar = user.name ? Util.esc(user.name[0]) : Ico.user;
    render(
      navbar("编辑资料", { back: true })
      + '<div class="page-pad profile-wrap">'
      + '<div class="card profile-card">'
      + '<button type="button" class="profile-avatar" id="pf-avatar" aria-label="更换头像" title="点击更换头像"></button>'
      + '<button type="button" class="pa-remove hidden" id="pf-avatar-remove">移除头像</button>'
      + '<input type="file" id="pf-avatar-file" accept="image/*" class="hidden">'
      + '<div class="field"><label>用户名</label><div class="pf-value">' + Util.esc(user.username || "") + '</div></div>'
      + '<div class="field"><label>姓名</label><input id="pf-name" type="text" placeholder="请输入姓名" value="' + Util.esc(user.name || "") + '" maxlength="20"></div>'
      + '<div class="field"><label>手机号</label><input id="pf-phone" type="tel" placeholder="请输入手机号" value="' + Util.esc(user.phone || "") + '" maxlength="11"></div>'
      + '<div class="field"><label>性别</label><div class="field-row pf-sex">'
      +   '<label class="pf-radio"><input type="radio" name="pf-sex" value="1"' + (user.sex === "1" ? " checked" : "") + '><span>男</span></label>'
      +   '<label class="pf-radio"><input type="radio" name="pf-sex" value="0"' + (user.sex === "0" ? " checked" : "") + '><span>女</span></label>'
      +   '<label class="pf-radio"><input type="radio" name="pf-sex" value=""' + (!user.sex ? " checked" : "") + '><span>保密</span></label>'
      + '</div></div>'
      + "</div>"
      + '<button class="btn-primary" id="pf-save">保存</button>'
      + "</div>", { title: "编辑资料" });

    // —— 头像：本地选择 → 居中裁剪压缩 → localStorage 仅本地生效 ——
    const avatarEl = document.getElementById("pf-avatar");
    const fileEl = document.getElementById("pf-avatar-file");
    const removeEl = document.getElementById("pf-avatar-remove");
    const camBadge = '<span class="pa-cam">' + Ico.camera + "</span>";
    function paintAvatar() {
      const url = Api.getAvatar();
      avatarEl.innerHTML = (url ? '<img src="' + url + '" alt="头像">' : avatarChar) + camBadge;
      removeEl.classList.toggle("hidden", !url);
    }
    paintAvatar();
    avatarEl.addEventListener("click", function () { fileEl.click(); });
    fileEl.addEventListener("change", async function () {
      const file = fileEl.files && fileEl.files[0];
      if (!file) return;
      try {
        const dataUrl = await fileToAvatarDataUrl(file);
        Api.setAvatar(dataUrl);
        paintAvatar();
        Util.toast("头像已更换");
      } catch (e) {
        Util.toast(e.message || "头像设置失败");
      } finally {
        fileEl.value = "";
      }
    });
    removeEl.addEventListener("click", async function () {
      if (!(await Util.confirm("确定移除当前头像吗？"))) return;
      Api.clearAvatar();
      paintAvatar();
      Util.toast("已恢复默认头像");
    });

    document.getElementById("pf-save").addEventListener("click", async () => {
      const name = document.getElementById("pf-name").value.trim();
      const phone = document.getElementById("pf-phone").value.trim();
      const sexEl = document.querySelector('input[name="pf-sex"]:checked');
      const sex = sexEl ? sexEl.value : "";
      if (!name) { Util.toast("姓名不能为空"); return; }
      if (phone && !/^1\d{10}$/.test(phone)) { Util.toast("手机号格式不正确"); return; }
      const btn = document.getElementById("pf-save");
      btn.disabled = true;
      try {
        await Api.updateUserInfo({ name, phone, sex });
        // 更新本地缓存中的用户昵称，我的页头部同步生效
        const cached = Api.getUser() || {};
        cached.name = name;
        Api.setUser(cached);
        Util.toast("保存成功");
        setTimeout(() => App.go("my"), 600);
      } catch (e) {
        Util.toast(e.message || "保存失败");
      } finally {
        btn.disabled = false;
      }
    });
  }

  // ==================== 事件注册 ====================
  window.App.submitAuth = async function (mode) {
    const username = document.getElementById("a-username").value.trim();
    const password = document.getElementById("a-password").value;
    if (!username || !password) { Util.toast("请输入用户名和密码"); return; }
    try {
      if (mode === "login") {
        const res = await Api.login(username, password);
        Api.setAuth(res.token, { id: res.id, username: res.username, name: res.name, role: res.role });
        Util.toast("欢迎，" + (res.name || res.username));
        location.hash = "#/menu";
      } else {
        const name = (document.getElementById("a-name").value || "").trim();
        const phone = (document.getElementById("a-phone").value || "").trim();
        await Api.register({ username, password, name, phone, role: 1 });
        Util.toast("注册成功，请登录");
        location.hash = "#/login";
      }
    } catch (e) {
      Util.toast(e.message);
    }
  };

  window.App.logout = async function () {
    if (!(await Util.confirm("确定退出登录吗？"))) return;
    closeChatSocket();
    Api.clearAuth();
    location.hash = "#/login";
  };

  window.addEventListener("hashchange", router);
  router();
})();
