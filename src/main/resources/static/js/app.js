/**
 * HostelEats Client-Side Controller
 * Pure ES6+ JavaScript, event-driven state management & REST API client
 */

const state = {
  token: localStorage.getItem('hosteleats_token') || null,
  user: null,
  role: 'guest',
  activeTab: 'panel-menu',
  menu: [],
  categories: ['All Items'],
  selectedCategory: 'All Items',
  tray: [], // [{ id, name, price, qty, emoji }]
  orders: [],
  bookedSlots: [],
  mergedSlots: [],
  rateLimitStatus: { used: 0, max: 3, windowSeconds: 30 }
};

// --- Initialization ---
document.addEventListener('DOMContentLoaded', async () => {
  initEventListeners();
  updateReloadChip();

  // Load baseline data
  await checkSession();
  await loadMenu();
  await updateRateLimitStatus();
  await loadOrders();
  await loadSlots();
  await loadRushHour(2);
});

function updateReloadChip() {
  const chip = document.getElementById('reload-chip');
  if (chip) {
    const timeStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    chip.textContent = `Last reload: ${timeStr}`;
  }
}

// --- Event Listeners Setup ---
function initEventListeners() {
  // Navigation tabs
  const tabButtons = document.querySelectorAll('.tab-btn');
  tabButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const targetPanel = btn.getAttribute('data-tab');
      switchTab(targetPanel);
    });
  });

  // Hero Quick CTAs
  document.getElementById('hero-menu-btn')?.addEventListener('click', () => switchTab('panel-menu'));
  document.getElementById('hero-combo-btn')?.addEventListener('click', () => switchTab('panel-combo'));

  // Auth Modal Triggers
  const loginOpenBtn = document.getElementById('login-open');
  const loginCloseBtn = document.getElementById('login-close');
  const loginModal = document.getElementById('login-modal');

  loginOpenBtn?.addEventListener('click', () => {
    if (state.token) {
      logout();
    } else {
      loginModal?.classList.remove('hidden');
    }
  });

  loginCloseBtn?.addEventListener('click', () => {
    loginModal?.classList.add('hidden');
  });

  // Quick Demo Credential buttons
  document.getElementById('demo-student-btn')?.addEventListener('click', () => {
    document.getElementById('login-username').value = 'aarav';
    document.getElementById('login-password').value = 'hostel123';
  });

  document.getElementById('demo-staff-btn')?.addEventListener('click', () => {
    document.getElementById('login-username').value = 'canteen';
    document.getElementById('login-password').value = 'kitchen123';
  });

  // Login Form Submit
  document.getElementById('login-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    await handleLogin();
  });

  // Place Order Button
  document.getElementById('place-order-btn')?.addEventListener('click', async () => {
    await handlePlaceOrder();
  });

  // Combo Builder Interactions
  const budgetSlider = document.getElementById('combo-budget-slider');
  const budgetValDisplay = document.getElementById('combo-budget-val');
  const presetBtns = document.querySelectorAll('.budget-preset-btn');

  budgetSlider?.addEventListener('input', (e) => {
    const val = e.target.value;
    if (budgetValDisplay) budgetValDisplay.textContent = `₹${val}`;
    presetBtns.forEach(b => {
      if (b.getAttribute('data-val') === val) b.classList.add('active');
      else b.classList.remove('active');
    });
  });

  presetBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const val = btn.getAttribute('data-val');
      if (budgetSlider) budgetSlider.value = val;
      if (budgetValDisplay) budgetValDisplay.textContent = `₹${val}`;
      presetBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
    });
  });

  document.getElementById('combo-recommend-btn')?.addEventListener('click', async () => {
    const budget = parseInt(budgetSlider?.value || '60', 10);
    await loadComboRecommendation(budget);
  });

  // Slots
  document.getElementById('refresh-slots-btn')?.addEventListener('click', async () => {
    await loadSlots();
  });

  document.getElementById('reserve-slot-btn')?.addEventListener('click', async () => {
    await handleReserveSlot();
  });

  // Orders Refresh
  document.getElementById('refresh-orders-btn')?.addEventListener('click', async () => {
    await loadOrders();
  });

  // Rush Hour
  document.getElementById('rush-window-select')?.addEventListener('change', async (e) => {
    const win = parseInt(e.target.value, 10);
    await loadRushHour(win);
  });

  // Campus Walk
  document.getElementById('calc-route-btn')?.addEventListener('click', async () => {
    await handleCalculateRoute();
  });

  // Kitchen Console Refresh
  document.getElementById('refresh-kitchen-btn')?.addEventListener('click', async () => {
    await loadOrders();
  });

  // Admin Reset
  document.getElementById('admin-reset-btn')?.addEventListener('click', async () => {
    if (confirm('Reset challenge dataset to seed state?')) {
      await handleAdminReset();
    }
  });
}

// --- Tab Switching ---
function switchTab(panelId) {
  state.activeTab = panelId;
  document.querySelectorAll('.tab-btn').forEach(b => {
    if (b.getAttribute('data-tab') === panelId) b.classList.add('active');
    else b.classList.remove('active');
  });

  document.querySelectorAll('.panel-tab').forEach(p => {
    if (p.id === panelId) p.classList.add('active');
    else p.classList.remove('active');
  });

  if (panelId === 'panel-orders') loadOrders();
  if (panelId === 'panel-kitchen') loadOrders();
  if (panelId === 'panel-slots') loadSlots();
}

// --- Auth Operations ---
async function checkSession() {
  if (!state.token) {
    updateAuthUI(null);
    return;
  }

  try {
    const res = await fetch('/api/auth/session', {
      headers: { 'Authorization': `Bearer ${state.token}` }
    });

    if (res.ok) {
      const data = await res.json();
      state.user = data.name;
      state.role = data.role;
      updateAuthUI(data);
    } else {
      // Session invalid or expired
      logout(false);
    }
  } catch (e) {
    console.error('Session check failed', e);
  }
}

async function handleLogin() {
  const username = document.getElementById('login-username').value;
  const password = document.getElementById('login-password').value;
  const errorBox = document.getElementById('login-error');

  try {
    const res = await fetch('/api/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });

    const data = await res.json();
    if (res.ok && data.token) {
      state.token = data.token;
      state.user = data.name;
      state.role = data.role;
      localStorage.setItem('hosteleats_token', data.token);

      document.getElementById('login-modal').classList.add('hidden');
      errorBox.classList.add('hidden');

      // Fetch active session from backend to get the real expires_at value
      await checkSession();

      await updateRateLimitStatus();
      await loadOrders();
    } else {
      errorBox.textContent = data.error || 'Invalid username or password';
      errorBox.classList.remove('hidden');
    }
  } catch (err) {
    errorBox.textContent = 'Server connection failed';
    errorBox.classList.remove('hidden');
  }
}

let sessionTimerInterval = null;

function logout(callApi = true) {
  if (sessionTimerInterval) {
    clearInterval(sessionTimerInterval);
    sessionTimerInterval = null;
  }
  const timerChip = document.getElementById('session-timer-chip');
  if (timerChip) timerChip.style.display = 'none';

  if (callApi && state.token) {
    fetch('/api/logout', {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${state.token}` }
    }).catch(() => {});
  }

  state.token = null;
  state.user = null;
  state.role = 'guest';
  localStorage.removeItem('hosteleats_token');
  updateAuthUI(null);
  updateRateLimitStatus();
  loadOrders();
}

function updateAuthUI(session) {
  const accountName = document.getElementById('account-name');
  const roleBadge = document.getElementById('account-role-badge');
  const loginOpenBtn = document.getElementById('login-open');
  const kitchenTabBtn = document.getElementById('kitchen-tab-btn');
  const timerChip = document.getElementById('session-timer-chip');
  const timerText = document.getElementById('session-timer-text');
  const timerLabel = document.getElementById('session-timer-label');
  const accountChip = document.getElementById('account-chip');

  if (session && state.token) {
    accountName.textContent = session.name;
    roleBadge.textContent = session.role;
    roleBadge.style.display = 'inline-block';
    loginOpenBtn.textContent = 'Sign out';

    if (timerChip) {
      timerChip.style.display = 'inline-flex';

      if (sessionTimerInterval) {
        clearInterval(sessionTimerInterval);
        sessionTimerInterval = null;
      }

      // Check the actual session expires_at from the backend API
      const rawExpiresAt = session.expires_at;
      const nowSec = Date.now() / 1000;

      // When Bug 1 is present, expires_at is stored in milliseconds (~1.7e12) instead of seconds (~1.7e9)
      // which results in hundreds of years (~50,000+ years), showing Indefinite / ∞ on UI!
      if (rawExpiresAt && rawExpiresAt > 1e11) {
        // Bug 1 is present: backend stored milliseconds, session is indefinite
        timerChip.classList.add('indefinite');
        timerLabel.textContent = 'Session: ';
        timerText.textContent = '∞ Indefinite (Bug 1)';
        const bugMsg = `Bug 1 Active: expires_at is ${Math.round(rawExpiresAt)} ms (millisecond unit mismatch vs seconds timestamp). Session never expires!`;
        timerChip.setAttribute('title', bugMsg);
        if (accountChip) accountChip.setAttribute('title', bugMsg);
      } else {
        // Normal / fixed countdown
        timerChip.classList.remove('indefinite');
        timerLabel.textContent = 'Logout in: ';
        let secondsLeft = rawExpiresAt ? Math.max(0, Math.round(rawExpiresAt - nowSec)) : 30;

        const updateDisplay = () => {
          timerText.textContent = `${secondsLeft}s`;
          if (accountChip) {
            accountChip.setAttribute('title', `Logged in as ${session.name} · Session expires in ${secondsLeft}s`);
          }
        };

        updateDisplay();
        sessionTimerInterval = setInterval(() => {
          secondsLeft--;
          if (secondsLeft <= 0) {
            clearInterval(sessionTimerInterval);
            sessionTimerInterval = null;
            logout(false);
          } else {
            updateDisplay();
          }
        }, 1000);
      }
    }

    if (session.role.toLowerCase() === 'staff') {
      kitchenTabBtn?.classList.remove('hidden');
    } else {
      kitchenTabBtn?.classList.add('hidden');
      if (state.activeTab === 'panel-kitchen') switchTab('panel-menu');
    }
  } else {
    if (sessionTimerInterval) {
      clearInterval(sessionTimerInterval);
      sessionTimerInterval = null;
    }
    if (timerChip) timerChip.style.display = 'none';
    if (accountChip) accountChip.removeAttribute('title');

    accountName.textContent = 'Guest';
    roleBadge.textContent = 'Guest';
    roleBadge.style.display = 'none';
    loginOpenBtn.textContent = 'Sign in';
    kitchenTabBtn?.classList.add('hidden');
    if (state.activeTab === 'panel-kitchen') switchTab('panel-menu');
  }
}

// --- Menu & Tray Management ---
async function loadMenu() {
  try {
    const res = await fetch('/api/menu');
    const data = await res.json();
    state.menu = data.items || [];
    renderMenu();
  } catch (e) {
    console.error('Failed to load menu', e);
  }
}

function renderMenu() {
  const grid = document.getElementById('menu-grid');
  if (!grid) return;

  const items = state.selectedCategory === 'All Items'
    ? state.menu
    : state.menu.filter(i => i.category === state.selectedCategory);

  grid.innerHTML = items.map(item => `
    <div class="menu-card">
      <div>
        <div class="menu-card-top">
          <div class="item-emoji">${item.emoji}</div>
          ${item.popular ? '<span class="badge-popular">★ POPULAR</span>' : ''}
        </div>
        <div class="item-category">${item.category}</div>
        <div class="item-title">${item.name}</div>
        <div class="item-meta">
          <span class="item-price">₹${item.price}</span>
          <span class="item-rating">Satisfaction: ${item.satisfaction}/10</span>
        </div>
      </div>
      <button class="btn-add-tray" onclick="addToTray('${item.id}')">
        + Add to Tray
      </button>
    </div>
  `).join('');

  // Setup category pills listener
  const catPills = document.querySelectorAll('.cat-pill');
  catPills.forEach(pill => {
    pill.onclick = () => {
      catPills.forEach(p => p.classList.remove('active'));
      pill.classList.add('active');
      state.selectedCategory = pill.getAttribute('data-cat');
      renderMenu();
    };
  });
}

window.addToTray = function(itemId) {
  const menuItem = state.menu.find(i => i.id === itemId);
  if (!menuItem) return;

  const existing = state.tray.find(i => i.id === itemId);
  if (existing) {
    existing.qty += 1;
  } else {
    state.tray.push({
      id: menuItem.id,
      name: menuItem.name,
      price: menuItem.price,
      emoji: menuItem.emoji,
      qty: 1
    });
  }
  renderTray();
};

window.modifyTrayQty = function(itemId, delta) {
  const itemIndex = state.tray.findIndex(i => i.id === itemId);
  if (itemIndex > -1) {
    state.tray[itemIndex].qty += delta;
    if (state.tray[itemIndex].qty <= 0) {
      state.tray.splice(itemIndex, 1);
    }
  }
  renderTray();
};

function renderTray() {
  const container = document.getElementById('tray-items');
  const countBadge = document.getElementById('tray-count');
  const totalPriceElem = document.getElementById('tray-total-price');
  const placeBtn = document.getElementById('place-order-btn');

  const totalItems = state.tray.reduce((acc, i) => acc + i.qty, 0);
  const totalPrice = state.tray.reduce((acc, i) => acc + (i.price * i.qty), 0);

  countBadge.textContent = `${totalItems} item${totalItems === 1 ? '' : 's'}`;
  totalPriceElem.textContent = `₹${totalPrice}`;

  if (state.tray.length === 0) {
    container.innerHTML = '<div class="tray-empty">Your tray is empty. Tap "+ Add to Tray" to get started!</div>';
    placeBtn.disabled = true;
  } else {
    container.innerHTML = state.tray.map(item => `
      <div class="tray-row">
        <div class="tray-row-info">
          <h5>${item.emoji} ${item.name}</h5>
          <span>₹${item.price} x ${item.qty} = ₹${item.price * item.qty}</span>
        </div>
        <div class="tray-row-controls">
          <button class="btn-qty" onclick="modifyTrayQty('${item.id}', -1)">-</button>
          <span>${item.qty}</span>
          <button class="btn-qty" onclick="modifyTrayQty('${item.id}', 1)">+</button>
        </div>
      </div>
    `).join('');
    placeBtn.disabled = false;
  }
}

// --- Rate Limiting & Order Submission ---
async function updateRateLimitStatus() {
  const userId = (state.token && state.user) ? 'aarav' : 'guest';
  try {
    const res = await fetch(`/api/limiter/status?userId=${userId}`);
    if (res.ok) {
      const data = await res.json();
      state.rateLimitStatus = data;
      const info = document.getElementById('limiter-info');
      const bar = document.getElementById('limiter-bar');
      if (info) {
        info.textContent = `${data.used} / ${data.max} orders used in the current ${data.windowSeconds}-second window.`;
      }
      if (bar) {
        const pct = Math.min(100, (data.used / data.max) * 100);
        bar.style.width = `${pct}%`;
        bar.style.backgroundColor = (data.used >= data.max) ? '#ef4444' : '#10b981';
      }
    }
  } catch (e) {
    console.error('Rate limit check failed', e);
  }
}

async function handlePlaceOrder() {
  if (state.tray.length === 0) return;

  const pickupSelect = document.getElementById('pickup-time-select');
  const pickupTime = pickupSelect ? pickupSelect.value : 'Immediate';
  const orderMsg = document.getElementById('order-msg');
  const placeBtn = document.getElementById('place-order-btn');

  placeBtn.disabled = true;
  placeBtn.textContent = 'Placing order...';

  try {
    const headers = { 'Content-Type': 'application/json' };
    if (state.token) headers['Authorization'] = `Bearer ${state.token}`;

    const res = await fetch('/api/orders', {
      method: 'POST',
      headers: headers,
      body: JSON.stringify({
        items: state.tray,
        pickup_time: pickupTime
      })
    });

    const data = await res.json();
    await updateRateLimitStatus();

    if (res.ok && data.allowed) {
      orderMsg.className = 'form-feedback success';
      orderMsg.textContent = `Order ${data.order.id} confirmed! Pickup: ${data.order.pickupTime}`;
      orderMsg.classList.remove('hidden');

      state.tray = [];
      renderTray();
      await loadOrders();
    } else {
      orderMsg.className = 'form-feedback error';
      orderMsg.textContent = data.error || 'Failed to place order';
      orderMsg.classList.remove('hidden');
    }
  } catch (e) {
    orderMsg.className = 'form-feedback error';
    orderMsg.textContent = 'Server connection error';
    orderMsg.classList.remove('hidden');
  } finally {
    placeBtn.disabled = state.tray.length === 0;
    placeBtn.textContent = 'Place Order Now';
  }
}

// --- Orders Listing & Tracking ---
async function loadOrders() {
  const container = document.getElementById('orders-container');
  if (!container) return;

  try {
    const headers = {};
    if (state.token) headers['Authorization'] = `Bearer ${state.token}`;

    const res = await fetch('/api/orders', { headers });
    if (res.status === 401) {
      container.innerHTML = `
        <div class="placeholder-state">
          Please <a href="javascript:void(0)" onclick="document.getElementById('login-modal').classList.remove('hidden')" style="color: var(--amber-primary)">sign in</a> to view your active pre-orders and live progress tracker.
        </div>
      `;
      return;
    }

    const data = await res.json();
    state.orders = data.orders || [];

    if (state.role === 'staff') {
      renderKitchenKanban(state.orders);
    }

    renderOrders(state.orders);
  } catch (e) {
    console.error('Error fetching orders', e);
  }
}

function renderOrders(orders) {
  const container = document.getElementById('orders-container');
  if (!container) return;

  if (orders.length === 0) {
    container.innerHTML = '<div class="placeholder-state">No orders found. Pre-order some delicious meals from the Menu tab!</div>';
    return;
  }

  const steps = ['Queued', 'Preparing', 'Ready', 'Completed'];

  container.innerHTML = orders.map(order => {
    const currentIdx = steps.indexOf(order.status);
    const itemsSummary = (order.items || []).map(i => `${i.name} (x${i.qty})`).join(', ');

    return `
      <div class="order-tracker-card">
        <div class="order-top-row">
          <div>
            <span class="order-id-badge">${order.id}</span>
            <span style="margin-left: 0.5rem; font-size: 0.85rem; color: var(--text-muted)">User: ${order.userName}</span>
          </div>
          <div>
            <span class="chip">Scheduled: ${order.pickupTime}</span>
            <span class="chip" style="margin-left: 0.5rem; color: var(--amber-primary)">Total: ₹${order.total}</span>
          </div>
        </div>

        <div class="order-progress-steps">
          ${steps.map((st, idx) => {
            let cls = '';
            if (idx < currentIdx) cls = 'done';
            else if (idx === currentIdx) cls = 'current';

            return `
              <div class="progress-step ${cls}">
                <div class="step-node">${idx < currentIdx ? '✓' : idx + 1}</div>
                <div class="step-label">${st}</div>
              </div>
            `;
          }).join('')}
        </div>

        <div class="order-items-summary">
          <span>Items: ${itemsSummary}</span>
          <span>Status: <strong>${order.status}</strong></span>
        </div>
      </div>
    `;
  }).join('');
}

// --- Kitchen Kanban Console (Staff) ---
function renderKitchenKanban(orders) {
  const queuedCol = document.getElementById('cards-queued');
  const prepCol = document.getElementById('cards-preparing');
  const readyCol = document.getElementById('cards-ready');

  const countQueued = document.getElementById('count-queued');
  const countPrep = document.getElementById('count-preparing');
  const countReady = document.getElementById('count-ready');

  if (!queuedCol || !prepCol || !readyCol) return;

  const queuedOrders = orders.filter(o => o.status === 'Queued');
  const prepOrders = orders.filter(o => o.status === 'Preparing');
  const readyOrders = orders.filter(o => o.status === 'Ready');

  if (countQueued) countQueued.textContent = queuedOrders.length;
  if (countPrep) countPrep.textContent = prepOrders.length;
  if (countReady) countReady.textContent = readyOrders.length;

  const createCard = (order, nextStatus, nextLabel) => `
    <div class="kanban-ticket">
      <div class="kanban-ticket-top">
        <span class="ticket-id">${order.id}</span>
        <span class="ticket-user">${order.userName} · ${order.pickupTime}</span>
      </div>
      <div class="ticket-items">
        ${(order.items || []).map(i => `${i.name} (x${i.qty})`).join(', ')}
      </div>
      ${nextStatus ? `
        <button class="btn btn-xs btn-primary btn-block" onclick="updateOrderStatus('${order.id}', '${nextStatus}')">
          ${nextLabel}
        </button>
      ` : ''}
    </div>
  `;

  queuedCol.innerHTML = queuedOrders.map(o => createCard(o, 'Preparing', 'Start Prep ➔')).join('');
  prepCol.innerHTML = prepOrders.map(o => createCard(o, 'Ready', 'Mark Ready ➔')).join('');
  readyCol.innerHTML = readyOrders.map(o => createCard(o, 'Completed', 'Complete & Dispatch ✓')).join('');
}

window.updateOrderStatus = async function(orderId, nextStatus) {
  try {
    const res = await fetch(`/api/orders/${orderId}/status`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${state.token}`
      },
      body: JSON.stringify({ status: nextStatus })
    });

    if (res.ok) {
      await loadOrders();
    } else {
      alert('Failed to update status');
    }
  } catch (e) {
    console.error('Error updating status', e);
  }
};

// --- Combo Recommendation ---
async function loadComboRecommendation(budget) {
  const container = document.getElementById('combo-results');
  if (!container) return;

  try {
    const res = await fetch(`/api/combo/recommend?budget=${budget}`, {
      method: 'POST'
    });
    const data = await res.json();

    const items = data.items || [];
    if (items.length === 0) {
      container.innerHTML = '<div class="placeholder-state">No combo possible for this budget. Try increasing your budget!</div>';
      return;
    }

    container.innerHTML = `
      <div class="combo-res-card">
        <div>
          <div class="combo-res-stats">
            <div class="stat-item">
              <h4>Total Cost</h4>
              <span>₹${data.total_cost}</span>
            </div>
            <div class="stat-item">
              <h4>Satisfaction</h4>
              <span>${data.total_satisfaction} pts</span>
            </div>
          </div>
          <div class="combo-items-chips">
            ${items.map(item => `
              <div class="combo-chip">
                <span>${item.emoji}</span>
                <strong>${item.name}</strong>
                <span>(₹${item.price})</span>
              </div>
            `).join('')}
          </div>
        </div>
        <button class="btn btn-primary" onclick="addComboToTray(${JSON.stringify(items).replace(/"/g, '&quot;')})">
          Add Combo to Tray 🛒
        </button>
      </div>
    `;
  } catch (e) {
    console.error('Combo recommendation error', e);
  }
}

window.addComboToTray = function(items) {
  items.forEach(item => {
    window.addToTray(item.id);
  });
  switchTab('panel-menu');
};

// --- Pickup Slots Timeline ---
async function loadSlots() {
  try {
    const res = await fetch('/api/slots');
    const data = await res.json();
    state.bookedSlots = data.booked_slots || [];
    state.mergedSlots = data.merged_slots || [];

    renderSlotsTimeline(state.mergedSlots);
  } catch (e) {
    console.error('Failed to load slots', e);
  }
}

function renderSlotsTimeline(mergedSlots) {
  const timelineBar = document.getElementById('timeline-bar');
  const chipsContainer = document.getElementById('merged-intervals-chips');
  if (!timelineBar) return;

  // Day window: 8:00 AM (480 mins) to 9:00 PM (1260 mins) = total 780 mins
  const dayStart = 480;
  const dayEnd = 1260;
  const totalMins = dayEnd - dayStart;

  timelineBar.innerHTML = mergedSlots.map(interval => {
    const start = Math.max(dayStart, interval[0]);
    const end = Math.min(dayEnd, interval[1]);
    const leftPct = ((start - dayStart) / totalMins) * 100;
    const widthPct = Math.max(1, ((end - start) / totalMins) * 100);

    return `<div class="timeline-busy-segment" style="left: ${leftPct}%; width: ${widthPct}%;"></div>`;
  }).join('');

  if (chipsContainer) {
    chipsContainer.innerHTML = mergedSlots.map(i => {
      const startH = Math.floor(i[0] / 60);
      const startM = (i[0] % 60).toString().padStart(2, '0');
      const endH = Math.floor(i[1] / 60);
      const endM = (i[1] % 60).toString().padStart(2, '0');
      return `<span class="interval-chip">🔒 ${startH}:${startM} – ${endH}:${endM} (${i[0]}m – ${i[1]}m)</span>`;
    }).join('');
  }
}

async function handleReserveSlot() {
  const startSelect = document.getElementById('slot-start-time');
  const durationSelect = document.getElementById('slot-duration');
  const feedback = document.getElementById('slot-feedback');

  const start = parseInt(startSelect.value, 10);
  const duration = parseInt(durationSelect.value, 10);
  const end = start + duration;

  try {
    const res = await fetch('/api/slots/reserve', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ start, end })
    });

    const data = await res.json();
    if (res.ok && data.success) {
      feedback.className = 'form-feedback success';
      feedback.textContent = `Successfully reserved slot [${start}m, ${end}m]!`;
      feedback.classList.remove('hidden');
      await loadSlots();
    } else {
      feedback.className = 'form-feedback error';
      feedback.textContent = data.error || 'Requested slot is unavailable';
      feedback.classList.remove('hidden');
    }
  } catch (e) {
    feedback.className = 'form-feedback error';
    feedback.textContent = 'Server connection error';
    feedback.classList.remove('hidden');
  }
}

// --- Rush Hour Analytics ---
async function loadRushHour(windowSize = 2) {
  try {
    const res = await fetch(`/api/analytics/rush-hour?window=${windowSize}`);
    const data = await res.json();

    const counts = data.hourly_counts || [];
    const labels = data.hour_labels || [];
    const peakStart = data.peak_start_index || 0;
    const peakOrders = data.peak_window_orders || 0;
    const peakLabel = data.peak_window_label || '';

    // Update Callout Card
    const peakName = document.getElementById('peak-window-name');
    const peakDesc = document.getElementById('peak-window-desc');
    if (peakName) peakName.textContent = `Peak Window: ${peakLabel}`;
    if (peakDesc) peakDesc.textContent = `Cumulative load during peak: ${peakOrders} orders recorded.`;

    // Render Bars
    const chartBars = document.getElementById('chart-bars');
    if (!chartBars) return;

    const maxVal = Math.max(...counts, 1);
    chartBars.innerHTML = counts.map((cnt, idx) => {
      const isPeakWindow = idx >= peakStart && idx < (peakStart + windowSize);
      const heightPct = Math.round((cnt / maxVal) * 100);

      return `
        <div class="chart-col ${isPeakWindow ? 'highlight' : ''}">
          <span class="bar-val">${cnt}</span>
          <div class="bar-pillar" style="height: ${heightPct}%;"></div>
          <span class="bar-label">${labels[idx] || ''}</span>
        </div>
      `;
    }).join('');
  } catch (e) {
    console.error('Failed to load rush hour data', e);
  }
}

// --- Campus Walk Dijkstra Navigation ---
async function handleCalculateRoute() {
  const source = document.getElementById('walk-source').value;
  const target = document.getElementById('walk-target').value;
  const resultCard = document.getElementById('route-result');
  const minsElem = document.getElementById('route-minutes');
  const pathElem = document.getElementById('route-path-container');

  try {
    const res = await fetch(`/api/campus/route?source=${encodeURIComponent(source)}&target=${encodeURIComponent(target)}`);
    const data = await res.json();

    if (res.ok && data.minutes >= 0) {
      minsElem.textContent = data.minutes;
      const path = data.path || [];

      pathElem.innerHTML = path.map((node, idx) => `
        <span class="crumb-node">${node}</span>
        ${idx < path.length - 1 ? '<span class="crumb-arrow">➔</span>' : ''}
      `).join('');

      resultCard.classList.remove('hidden');

      // Highlight active path badges
      document.querySelectorAll('.node-badge').forEach(badge => {
        const nodeName = badge.getAttribute('data-node');
        if (path.includes(nodeName)) {
          badge.classList.add('active-path');
        } else {
          badge.classList.remove('active-path');
        }
      });
    } else {
      minsElem.textContent = 'No path';
      pathElem.innerHTML = '<span>No reachable path found between selected locations.</span>';
      resultCard.classList.remove('hidden');
    }
  } catch (e) {
    console.error('Route calculation error', e);
  }
}

// --- Admin Reset ---
async function handleAdminReset() {
  const banner = document.getElementById('reloading-banner');
  banner?.classList.remove('hidden');

  try {
    await fetch('/api/admin/reset', { method: 'POST' });
    logout(false);
    await loadMenu();
    await loadOrders();
    await loadSlots();
    await loadRushHour(2);
    updateReloadChip();
  } catch (e) {
    console.error('Admin reset failed', e);
  } finally {
    setTimeout(() => {
      banner?.classList.add('hidden');
    }, 800);
  }
}
