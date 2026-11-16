const help = document.querySelector('#help-dialog');
document.querySelectorAll('[data-help]').forEach(button => button.addEventListener('click', () => {
  const invite = button.dataset.help === 'invite';
  document.querySelector('#help-title').textContent = invite ? 'Отримайте запрошення' : 'Відновіть доступ';
  document.querySelector('#help-content').innerHTML = invite
    ? '<ol><li>Зверніться до адміністратора вашого офісу.</li><li>Отримайте особисте посилання із запрошенням.</li><li>Відкрийте його та встановіть власний пароль.</li></ol><p>RoomBook доступний лише працівникам вашої команди.</p>'
    : '<p>Зверніться до адміністратора вашого офісу. Він допоможе відновити доступ до облікового запису.</p>';
  help.showModal();
}));
document.querySelectorAll('.dialog-close,.close-help').forEach(button => button.addEventListener('click', () => help.close()));
document.querySelector('#show-password').addEventListener('click', event => {
  const input = document.querySelector('#password');
  const show = input.type === 'password';
  input.type = show ? 'text' : 'password';
  event.currentTarget.textContent = show ? 'Сховати' : 'Показати';
  event.currentTarget.setAttribute('aria-label', show ? 'Сховати пароль' : 'Показати пароль');
});
document.querySelector('#login-form').addEventListener('submit', event => {
  event.preventDefault();
  document.querySelector('#password').value = '';
  const message = document.querySelector('#login-message');
  message.textContent = 'Це макет. Для перегляду розкладу скористайтеся посиланням «Переглянути демо без входу».';
  message.hidden = false;
});
