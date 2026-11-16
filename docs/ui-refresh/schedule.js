const rooms = [
  {id:1,name:'Альфа',floor:1,capacity:6,equipment:'Екран · дошка'},
  {id:2,name:'Бета',floor:1,capacity:4,equipment:'Дошка'},
  {id:3,name:'Калина',floor:1,capacity:10,equipment:'Екран · відеозв’язок'},
  {id:4,name:'Обрій',floor:2,capacity:8,equipment:'Екран · дошка'},
  {id:5,name:'Простір',floor:2,capacity:12,equipment:'Екран · відеозв’язок'},
  {id:6,name:'Тиша',floor:2,capacity:2,equipment:'Для розмов удвох'}
];
const demoDate = '2026-11-06';
const events = [
  [1,540,630,'Зустріч продукту','Андрій Мельник',false], [1,840,900,'Планування спринту','Олена Коваль',true],
  [2,660,720,'Інтерв’ю','Марія Бондар',false], [2,930,1020,'Дизайн-рев’ю','Дмитро Савчук',false],
  [3,630,750,'Зустріч команди','Андрій Мельник',false], [3,900,990,'Обговорення запуску','Марія Бондар',false],
  [4,540,600,'Плани на тиждень','Дмитро Савчук',false], [4,960,1020,'Розмова з командою','Олена Коваль',true],
  [5,600,720,'Робоча сесія','Марія Бондар',false], [5,810,900,'Огляд результатів','Андрій Мельник',false],
  [6,570,630,'Співбесіда','Дмитро Савчук',false], [6,780,840,'Зворотний зв’язок','Марія Бондар',false]
].map(([room,start,end,title,author,mine])=>({room,start,end,title,author,mine,date:demoDate}));
const $ = selector => document.querySelector(selector);
let mineOnly = false;
const time = minutes => `${String(Math.floor(minutes/60)).padStart(2,'0')}:${String(minutes%60).padStart(2,'0')}`;
const minutes = value => { const [h,m]=value.split(':').map(Number); return h*60+m; };
const formatDate = value => new Intl.DateTimeFormat('uk-UA',{weekday:'long',day:'numeric',month:'long',timeZone:'UTC'}).format(new Date(`${value}T12:00:00Z`));
function roomBusy(room) { return events.some(e=>e.room===room.id && e.date===demoDate && e.start<=675 && e.end>675); }
function render() {
  const date = $('#date').value || demoDate;
  $('#date').value = date;
  $('#date-heading').textContent = formatDate(date);
  $('#mine-count').textContent = events.filter(e=>e.mine).length;
  $('#free-count').textContent = rooms.filter(r=>!roomBusy(r)).length;
  const next = events.filter(e=>e.mine&&(e.date>demoDate||(e.date===demoDate&&e.start>=675))).sort((a,b)=>a.date.localeCompare(b.date)||a.start-b.start)[0];
  $('.next-meeting strong').textContent = next ? `Ваша наступна зустріч о ${time(next.start)}` : 'Попереду вільний розклад';
  $('.next-meeting small').textContent = next ? `${next.title} · ${rooms.find(r=>r.id===next.room).name} · ${next.end-next.start} хв` : 'Заплануйте наступну зустріч';
  $('#page-title').textContent = mineOnly ? 'Ваші зустрічі, ваш день' : 'Місце для вашої зустрічі';
  $('#all-view').classList.toggle('active',!mineOnly); $('#mine-view').classList.toggle('active',mineOnly);
  $('#all-view').setAttribute('aria-pressed',String(!mineOnly)); $('#mine-view').setAttribute('aria-pressed',String(mineOnly));
  const query=$('#search').value.toLocaleLowerCase('uk');
  const visible=rooms.filter(r=>r.name.toLocaleLowerCase('uk').includes(query) && (!$('#floor').value || r.floor===Number($('#floor').value)) && r.capacity>=Number($('#capacity').value) && (!$('#free-only').checked || !roomBusy(r)) && (!mineOnly || events.some(e=>e.mine&&e.room===r.id&&e.date===date)));
  const timeline=$('#timeline'); timeline.replaceChildren();
  const header=document.createElement('div'); header.className='time-header'; header.innerHTML='<div>Кімната / місцевий час</div><div class="hours">'+Array.from({length:12},(_,i)=>`<span>${time(480+i*60)}</span>`).join('')+(date===demoDate?'<i class="now-line" aria-label="Зараз 11:15"></i>':'')+'</div>';timeline.append(header);
  visible.forEach(room=>{
    const row=document.createElement('div');row.className='room-row';
    const info=document.createElement('div');info.className='room-info';info.innerHTML=`<div class="room-name"><span class="status-dot ${roomBusy(room)?'busy':''}" aria-label="${roomBusy(room)?'Зайнята зараз':'Вільна зараз'}"></span>${room.name}</div><div class="room-meta">${room.floor} поверх · до ${room.capacity} людей</div><div class="room-equipment">${room.equipment}</div>`;row.append(info);
    const slots=document.createElement('div');slots.className='slots';
    for(let i=0;i<48;i++){const start=480+i*15;const button=document.createElement('button');button.className='slot';button.setAttribute('aria-label',`Забронювати ${room.name}, ${time(start)}`);button.addEventListener('click',()=>openBooking(room.id,start));slots.append(button);}
    events.filter(e=>e.room===room.id&&e.date===date).forEach(event=>{
      const button=document.createElement('button');button.className=`event${event.mine?' mine':''}`;button.style.left=`${(event.start-480)/720*100}%`;button.style.width=`calc(${(event.end-event.start)/720*100}% - 4px)`;
      const title=document.createElement('strong');title.textContent=event.title;
      const duration=document.createElement('small');duration.textContent=`${time(event.start)}–${time(event.end)}${event.mine?' · Моє':''}`;
      button.append(title,duration);button.setAttribute('aria-label',`${event.title}, ${room.name}, ${time(event.start)}–${time(event.end)}, ${event.mine?'моє бронювання':event.author}`);
      button.addEventListener('click',()=>{$('#detail-title').textContent=event.title;$('#detail-copy').textContent=`${room.name} · ${formatDate(event.date)} · ${time(event.start)}–${time(event.end)}. Організатор: ${event.author}.`;$('#detail-dialog').showModal();});slots.append(button);
    });
    if(date===demoDate){const now=document.createElement('i');now.className='now-line';slots.append(now);}row.append(slots);timeline.append(row);
  });
  $('#empty').hidden=visible.length>0;$('#room-count').textContent=`Показано ${visible.length} із ${rooms.length} кімнат · 08:00–20:00`;
}
function openBooking(room=1,start=720){$('#booking-form').reset();$('#booking-room').value=String(room);$('#start').value=time(start);$('#end').value=time(Math.min(start+60,1200));$('#booking-error').hidden=true;$('#booking-date-label').textContent=formatDate($('#date').value);$('#booking-dialog').showModal();}
rooms.forEach(room=>{const option=document.createElement('option');option.value=room.id;option.textContent=`${room.name} · ${room.floor} поверх · до ${room.capacity} людей`;$('#booking-room').append(option);});
['#search','#floor','#capacity','#free-only','#date'].forEach(selector=>$(selector).addEventListener('input',render));
$('#mine-view').addEventListener('click',()=>{mineOnly=true;render();});$('#all-view').addEventListener('click',()=>{mineOnly=false;render();});
function changeDay(offset){const date=new Date(`${$('#date').value||demoDate}T12:00:00Z`);date.setUTCDate(date.getUTCDate()+offset);$('#date').value=date.toISOString().slice(0,10);render();}
$('#prev-day').addEventListener('click',()=>changeDay(-1));$('#next-day').addEventListener('click',()=>changeDay(1));$('#today').addEventListener('click',()=>{$('#date').value=demoDate;render();});$('#new-booking').addEventListener('click',()=>openBooking());
document.querySelectorAll('.dialog-close').forEach(button=>button.addEventListener('click',()=>button.closest('dialog').close()));$('#close-detail').addEventListener('click',()=>$('#detail-dialog').close());
$('#booking-form').addEventListener('submit',event=>{
  event.preventDefault();const room=Number($('#booking-room').value),start=minutes($('#start').value),end=minutes($('#end').value),date=$('#date').value,title=$('#meeting-title').value.trim();let error='';
  if(!title)error='Додайте назву зустрічі.';
  else if(!Number.isFinite(start)||!Number.isFinite(end)||start<480||end>1200||end<=start||end-start>240||start%15||end%15)error='Оберіть від 15 хвилин до 4 годин, з кроком 15 хвилин, між 08:00 і 20:00.';
  else if(date<demoDate||(date===demoDate&&start<675))error='Цей час уже минув. Оберіть майбутній інтервал.';
  else if(events.some(e=>e.date===date&&e.room===room&&start<e.end&&end>e.start))error='Кімната вже зайнята в цей час. Оберіть інший інтервал.';
  if(error){$('#booking-error').textContent=error;$('#booking-error').hidden=false;return;}
  events.push({room,start,end,title,date,mine:true,author:'Олена Коваль'});$('#booking-dialog').close();render();$('#toast').textContent=`Додано до деморозкладу: ${rooms.find(r=>r.id===room).name}, ${time(start)}–${time(end)}. Зміни зникнуть після оновлення сторінки.`;$('#toast').hidden=false;
});
render();
