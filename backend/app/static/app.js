const scheduleForm = document.getElementById("schedule-form");
const analyzeButton = document.getElementById("analyze-button");
const continueButton = document.getElementById("continue-button");
const addContactButton = document.getElementById("add-contact-button");
const contactsList = document.getElementById("contacts-list");
const contactRowTemplate = document.getElementById("contact-row-template");
const missingSection = document.getElementById("missing-section");
const missingFields = document.getElementById("missing-fields");
const confirmationCard = document.getElementById("confirmation-card");
const processingCard = document.getElementById("processing-card");
const confirmContact = document.getElementById("confirm-contact");
const confirmTime = document.getElementById("confirm-time");
const confirmMessage = document.getElementById("confirm-message");
const confirmButton = document.getElementById("confirm-button");
const editButton = document.getElementById("edit-button");
const refreshButton = document.getElementById("refresh-button");
const healthPill = document.getElementById("health-pill");
const feedbackOutput = document.getElementById("feedback-output");
const debugPanel = document.getElementById("debug-panel");
const userFilter = document.getElementById("user-filter");
const upcomingList = document.getElementById("upcoming-list");
const pastList = document.getElementById("past-list");
const upcomingCount = document.getElementById("upcoming-count");
const pastCount = document.getElementById("past-count");
const callCardTemplate = document.getElementById("call-card-template");
const homeView = document.getElementById("home-view");
const scheduledView = document.getElementById("scheduled-view");
const settingsPanel = document.getElementById("settings-panel");
const settingsToggle = document.getElementById("settings-toggle");
const homeTab = document.getElementById("home-tab");
const scheduledTab = document.getElementById("scheduled-tab");
const contactsTab = document.getElementById("contacts-tab");
const promptInput = document.getElementById("prompt-input");
const greetingLine = document.getElementById("greeting-line");
const greetingName = document.getElementById("greeting-name");

let currentAgentResult = null;
let currentView = "home";


function setView(viewName) {
  currentView = viewName;
  const showHome = viewName === "home";
  homeView.classList.toggle("view-panel-active", showHome);
  scheduledView.classList.toggle("view-panel-active", !showHome);
  homeTab.classList.toggle("active", showHome);
  scheduledTab.classList.toggle("active", !showHome);
  contactsTab.classList.toggle("active", false);
}


function resetConversationPanels() {
  processingCard.classList.add("hidden");
  missingSection.classList.add("hidden");
  confirmationCard.classList.add("hidden");
}


function getIstDate() {
  const now = new Date();
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Kolkata",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).formatToParts(now);
  const value = Object.fromEntries(parts.filter((part) => part.type !== "literal").map((part) => [part.type, part.value]));
  return {
    hour: Number(value.hour || "12"),
  };
}


function updateGreeting() {
  const userName = String(scheduleForm.elements.user_name.value || "").trim() || "Ayush";
  const { hour } = getIstDate();
  let timeGreeting = "Good evening,";
  if (hour < 12) {
    timeGreeting = "Good morning,";
  } else if (hour < 17) {
    timeGreeting = "Good afternoon,";
  }
  greetingLine.textContent = timeGreeting;
  greetingName.textContent = userName;
}


function showFeedback(payload) {
  feedbackOutput.textContent = JSON.stringify(payload, null, 2);
}


function addContactRow(name = "", number = "") {
  const fragment = contactRowTemplate.content.cloneNode(true);
  const row = fragment.querySelector(".contact-row");
  row.querySelector(".contact-row-name").value = name;
  row.querySelector(".contact-row-number").value = number;
  row.querySelector(".remove-contact-button").addEventListener("click", () => row.remove());
  contactsList.appendChild(fragment);
}


function readContacts() {
  return [...contactsList.querySelectorAll(".contact-row")]
    .map((row) => ({
      name: row.querySelector(".contact-row-name").value.trim(),
      number: row.querySelector(".contact-row-number").value.trim(),
    }))
    .filter((contact) => contact.name || contact.number);
}


function toIstIso(localValue) {
  return localValue ? `${localValue}:00+05:30` : "";
}


function toLocalDateTimeValue(isoValue) {
  if (!isoValue) {
    return "";
  }
  const date = new Date(isoValue);
  const formatter = new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Kolkata",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
  return formatter.format(date).replace(" ", "T");
}


function formatScheduledTime(isoValue) {
  if (!isoValue) {
    return "Not provided";
  }
  return new Intl.DateTimeFormat("en-IN", {
    dateStyle: "full",
    timeStyle: "short",
    timeZone: "Asia/Kolkata",
  }).format(new Date(isoValue));
}


function buildBaseRequest() {
  const formData = new FormData(scheduleForm);
  const requestBody = {
    user_id: String(formData.get("user_id") || "").trim(),
    user_name: String(formData.get("user_name") || "").trim(),
    raw_prompt: String(formData.get("raw_prompt") || "").trim(),
    contacts: readContacts(),
  };

  const missingTime = missingFields.querySelector('[name="scheduled_time"]');
  const missingNumber = missingFields.querySelector('[name="contact_number"]');
  const missingName = missingFields.querySelector('[name="contact_name"]');
  const missingMessage = missingFields.querySelector('[name="message"]');

  if (missingTime?.value) {
    requestBody.chosen_time = toIstIso(missingTime.value);
  }
  if (missingNumber?.value.trim()) {
    requestBody.confirmed_number = missingNumber.value.trim();
  }
  if (missingName?.value.trim()) {
    requestBody.confirmed_contact_name = missingName.value.trim();
  }
  if (missingMessage?.value.trim()) {
    requestBody.confirmed_message = missingMessage.value.trim();
  }
  return requestBody;
}


function createMissingField(fieldName, payload) {
  const label = document.createElement("label");
  label.className = "missing-field";

  const title = document.createElement("span");
  const fieldLabels = {
    contact_name: "Contact name",
    contact_number: "Contact number",
    message: "Message to convey",
    scheduled_time: "Call time in IST",
  };
  title.textContent = fieldLabels[fieldName] || fieldName;
  label.appendChild(title);

  const input = fieldName === "message" ? document.createElement("textarea") : document.createElement("input");
  input.name = fieldName;
  input.required = true;
  if (fieldName === "scheduled_time") {
    input.type = "datetime-local";
    input.value = toLocalDateTimeValue(payload.scheduled_time);
  } else if (fieldName === "contact_number") {
    input.type = "tel";
    input.placeholder = "+918879279251";
    input.value = payload.contact_number || "";
  } else if (fieldName === "message") {
    input.rows = 3;
    input.value = payload.agent_result?.message || "";
  } else {
    input.type = "text";
    input.value = payload.contact_name || "";
  }

  label.appendChild(input);
  return label;
}


function renderMissingFields(payload) {
  const fields = payload.missing_fields || [];
  missingFields.replaceChildren(...fields.map((field) => createMissingField(field, payload)));
  processingCard.classList.add("hidden");
  confirmationCard.classList.add("hidden");
  missingSection.classList.toggle("hidden", fields.length === 0);
  continueButton.textContent = "Continue";
  missingFields.querySelector("input, textarea")?.focus();
}


function showConfirmation(payload) {
  currentAgentResult = payload.agent_result;
  processingCard.classList.add("hidden");
  missingSection.classList.add("hidden");
  confirmationCard.classList.remove("hidden");
  confirmContact.textContent = `${payload.contact_name} / ${payload.contact_number}`;
  confirmTime.textContent = formatScheduledTime(payload.scheduled_time);
  confirmMessage.textContent = payload.message_preview;
  confirmationCard.scrollIntoView({ behavior: "smooth", block: "center" });
}


async function postScheduleCall(body) {
  const response = await fetch("/schedule-call", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const payload = await response.json();
  if (!response.ok) {
    throw new Error(payload.message || "The backend could not process this request.");
  }
  return payload;
}


async function checkHealth() {
  try {
    const response = await fetch("/health");
    const payload = await response.json();
    healthPill.textContent = payload.status === "ok" ? "Backend ready" : "Backend issue";
    healthPill.classList.toggle("health-pill-live", payload.status === "ok");
  } catch (error) {
    healthPill.textContent = "Backend offline";
    healthPill.classList.remove("health-pill-live");
  }
}


function renderCallList(container, calls, showCancel) {
  if (!calls.length) {
    container.className = "call-list empty-state";
    container.textContent = showCancel ? "No upcoming calls yet." : "No call history yet.";
    return;
  }
  container.className = "call-list";
  container.replaceChildren(...calls.map((call) => buildCallCard(call, showCancel)));
}


function buildCallCard(call, showCancel) {
  const fragment = callCardTemplate.content.cloneNode(true);
  fragment.querySelector(".contact-name").textContent = call.contact_name;
  fragment.querySelector(".meta-line").textContent = `${formatScheduledTime(call.scheduled_time)} / ${call.contact_number}`;
  fragment.querySelector(".message-line").textContent = call.rephrased_message || call.original_message;

  const statusBadge = fragment.querySelector(".status-badge");
  statusBadge.textContent = call.status;
  statusBadge.classList.add(call.status);

  const actions = fragment.querySelector(".card-actions");
  if (showCancel && call.status === "pending") {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "subtle-button danger-button";
    button.textContent = "Cancel";
    button.addEventListener("click", async () => {
      const response = await fetch(`/calls/${call.id}`, { method: "DELETE" });
      const payload = await response.json();
      showFeedback(payload);
      await loadCalls();
    });
    actions.appendChild(button);
  }
  return fragment;
}


async function loadCalls() {
  const userId = userFilter.value.trim();
  if (!userId) {
    return;
  }

  try {
    const response = await fetch(`/calls?user_id=${encodeURIComponent(userId)}`);
    const payload = await response.json();
    renderCallList(upcomingList, payload.upcoming || [], true);
    renderCallList(pastList, payload.past || [], false);
    upcomingCount.textContent = String((payload.upcoming || []).length);
    pastCount.textContent = String((payload.past || []).length);
  } catch (error) {
    showFeedback({ status: "error", message: "Unable to load calls." });
  }
}


function setProcessingState(isProcessing, buttonLabel) {
  analyzeButton.disabled = isProcessing;
  analyzeButton.textContent = buttonLabel;
  processingCard.classList.toggle("hidden", !isProcessing);
}


scheduleForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  setView("home");
  currentAgentResult = null;
  resetConversationPanels();
  setProcessingState(true, "Thinking...");

  try {
    const payload = await postScheduleCall(buildBaseRequest());
    showFeedback(payload);
    debugPanel.open = true;

    if (payload.status === "needs_info") {
      renderMissingFields(payload);
      analyzeButton.textContent = "Send";
    } else if (payload.status === "needs_confirmation") {
      showConfirmation(payload);
      analyzeButton.textContent = "Send";
    }
  } catch (error) {
    showFeedback({ status: "error", message: error.message });
    debugPanel.open = true;
    processingCard.classList.add("hidden");
    analyzeButton.textContent = "Try again";
  } finally {
    analyzeButton.disabled = false;
  }
});


confirmButton.addEventListener("click", async () => {
  if (!currentAgentResult) {
    return;
  }
  confirmButton.disabled = true;
  confirmButton.textContent = "Scheduling...";

  try {
    const body = buildBaseRequest();
    body.confirmed = true;
    body.agent_result = currentAgentResult;
    const payload = await postScheduleCall(body);
    showFeedback(payload);
    debugPanel.open = true;

    if (payload.status === "scheduled") {
      resetConversationPanels();
      currentAgentResult = null;
      promptInput.value = "";
      await loadCalls();
      setView("scheduled");
    } else if (payload.status === "needs_info") {
      renderMissingFields(payload);
    }
  } catch (error) {
    showFeedback({ status: "error", message: error.message });
    debugPanel.open = true;
  } finally {
    confirmButton.disabled = false;
    confirmButton.textContent = "Schedule call";
  }
});


editButton.addEventListener("click", () => {
  confirmationCard.classList.add("hidden");
  currentAgentResult = null;
  promptInput.focus();
});


function toggleSettings(forceOpen) {
  const shouldShow = typeof forceOpen === "boolean" ? forceOpen : settingsPanel.classList.contains("hidden");
  settingsPanel.classList.toggle("hidden", !shouldShow);
  settingsToggle.setAttribute("aria-expanded", String(shouldShow));
  contactsTab.classList.toggle("active", shouldShow);
}


settingsToggle.addEventListener("click", () => toggleSettings());


homeTab.addEventListener("click", () => setView("home"));
scheduledTab.addEventListener("click", async () => {
  setView("scheduled");
  toggleSettings(false);
  await loadCalls();
});
contactsTab.addEventListener("click", () => {
  setView("home");
  toggleSettings();
});

addContactButton.addEventListener("click", () => addContactRow());
refreshButton.addEventListener("click", loadCalls);
userFilter.addEventListener("change", loadCalls);

scheduleForm.elements.user_id.addEventListener("change", () => {
  userFilter.value = scheduleForm.elements.user_id.value.trim();
  loadCalls();
});
scheduleForm.elements.user_name.addEventListener("input", updateGreeting);

[...document.querySelectorAll(".suggestion-chip")].forEach((button) => {
  button.addEventListener("click", () => {
    promptInput.value = button.dataset.prompt || "";
    promptInput.focus();
  });
});


addContactRow("Ayush Waman", "+918879279251");
updateGreeting();
checkHealth();
loadCalls();
