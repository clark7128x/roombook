// Local enhancement only. Authentication, validation and bookings remain server-side.
const invitation = document.querySelector('#invitation-token');
if (invitation && /^[A-Za-z0-9_-]{43}$/.test(location.hash.slice(1))) {
  invitation.value = location.hash.slice(1);
  history.replaceState(null, '', location.pathname + location.search);
}
const invitationLink = document.querySelector('#invitation-link');
if (invitationLink) {
  invitationLink.value = new URL(invitationLink.value, location.origin).href;
  document.querySelector('#copy-invitation').addEventListener('click', async () => {
    const status = document.querySelector('#copy-status');
    try { await navigator.clipboard.writeText(invitationLink.value); status.textContent = 'Посилання скопійовано.'; }
    catch { invitationLink.select(); status.textContent = 'Виділено. Скопіюйте посилання вручну.'; }
  });
}
document.querySelectorAll('[data-show-password]').forEach(button => button.addEventListener('click', () => {
  const input = document.getElementById(button.dataset.showPassword);
  input.type = input.type === 'password' ? 'text' : 'password';
  button.textContent = input.type === 'password' ? 'Показати' : 'Сховати';
  button.setAttribute('aria-pressed', String(input.type === 'text'));
}));
