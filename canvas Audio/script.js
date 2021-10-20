const button1 = document.getElementById('button1');
let Audio1 = new Audio();
Audio1.src = 'music.mp3';

 button1.addEventListener('click', function(){
     Audio1.play();
     Audio1.addEventListener('playing', function(){
         console.log();
 });
 Audio1.addEventListener('ended', function(){
     
 })
});