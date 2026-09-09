const scheduleForm = document.getElementById("schedule-form");
const clearTimeButton = document.getElementById("clear-time-button");
const refreshButton = document.getElementById("refresh-button");
const healthPill = document.getElementById("health-pill");
const feedbackCard = document.getElementById("feedback-card");
const feedbackOutput = document.getElementById("feedback-output");
const userFilter = document.getElementById("user-filter");
const upcomingList = document.getElementById("upcoming-list");
const pastList = document.getElementById("past-list");
const upcomingCount = document.getElementById("upcoming-count");
const pastCount = document.getElementById("past-count");
const callCardTemplate = document.getElementById("call-card-template");


function showFeedback(payload) {
  feedbackCard.classList.remove("hidden");
  feedbackOutput.textContent = JSON.stringify(payload, null, 2);
}


function toIsoStringFromLocalInput(localValue) {
  if (!localValue) {
    return "";
  }

  // Adding the IST offset here keeps the backend and browser aligned on the same intended time.
  return `${localValue}:00+05:30`;
}


async function checkHealth() {
  try {
    const response = await fetch("/health");
    const payload = await response.json();
    healthPill.textContent = payload.status === "ok" ? "Server healthy" : "Server responded with an issue";
  } catch (error) {
    healthPill.textContent = "Server unreachable";
  }
}


async function loadCalls() {
  const userId = userFilter.value.trim();
  if (!userId) {
    return;
  }

  const response = await fetch(`/calls?user_id=${encodeURIComponent(userId)}`);
  const payload = await response.json();
  renderCallList(upcomingList, payload.upcoming || [], true);
  renderCallList(pastList, payload.past || [], false);
  upcomingCount.textContent = String((payload.upcoming || []).length);
  pastCount.textContent = String((payload.past || []).length);
}


function renderCallList(container, calls, showCancel) {
  if (!calls.length) {
    container.className = "call-list empty-state";
    container.textContent = showCancel ? "No upcoming calls yet." : "No past calls yet.";
    return;
  }

  container.className = "call-list";
  container.replaceChildren(...calls.map((call) => buildCallCard(call, showCancel)));
}


function buildCallCard(call, showCancel) {
  const fragment = callCardTemplate.content.cloneNode(true);
  fragment.querySelector(".contact-name").textContent = call.contact_name;
  fragment.querySelector(".meta-line").textContent = `${call.contact_number} • ${new Date(call.scheduled_time).toLocaleString()}`;
  fragment.querySelector(".message-line").textContent = call.rephrased_message || call.original_message;

  const statusBadge = fragment.querySelector(".status-badge");
  statusBadge.textContent = call.status;
  statusBadge.classList.add(call.status);

  const actions = fragment.querySelector(".card-actions");
  if (showCancel && call.status === "pending") {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "cancel-button";
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


scheduleForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const formData = new FormData(scheduleForm);
  const body = {
    user_id: String(formData.get("user_id") || "").trim(),
    user_name: String(formData.get("user_name") || "").trim(),
    contact_name: String(formData.get("contact_name") || "").trim(),
    contact_number: String(formData.get("contact_number") || "").trim(),
    message: String(formData.get("message") || "").trim(),
  };

  const chosenTime = toIsoStringFromLocalInput(String(formData.get("chosen_time") || "").trim());
  if (chosenTime) {
    body.chosen_time = chosenTime;
  }

  const response = await fetch("/schedule-call", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  const payload = await response.json();
  showFeedback(payload);

  if (payload.status === "needs_time") {
    scheduleForm.elements.chosen_time.focus();
  } else {
    await loadCalls();
  }
});


clearTimeButton.addEventListener("click", () => {
  scheduleForm.elements.chosen_time.value = "";
});


refreshButton.addEventListener("click", loadCalls);
userFilter.addEventListener("change", loadCalls);


checkHealth();
loadCalls();
