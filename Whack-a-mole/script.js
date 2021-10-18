const holes = document.querySelectorAll('.hole');
const scoreBoard = document.querySelector('.score');
const moles = document.querySelectorAll('.mole');
const countdownBoard = document.querySelectorAll('.countdown');
const startButton = document.querySelectorAll('button');

let lastHole;
let timeUp = false;
let timeLimit = 20000;
let score = 0;
let countdown;

function pickRandomHole(holes){
    const randomHole = Math.floor(Math.random() * holes.length);
    const hole = holes[randomHole];
    if (hole === lastHole){
        return pickRandomHole(holes);

    }
    lastHole = hole;
    return hole;
}
function popOut(){
    const time = Math.random() * 1300 + 400;
    const hole = pickRandomHole(holes);
    hole.classList.add('up');
    setTimeout(function(){
        hole.classList.remove('up');
        if (!timeUp){
            popOut();
        } else{
            scoreBoard.textContent = 'Times UP!! Thank you for saving our planet!';
       
        }
    },time);
}


function startGame(){
    countdown = timeLimit/1000;
    scoreBoard.textContent = 0;
    scoreBoard.style.display = 'block';
    countdownBoard.textContent = countdown;
    timeUp = false;
    score = 0;
    popOut();
    setTimeout(function(){
        time = true;
    }, timeLimit);

    let startCountdown = setInterval(function(){
        countdown -= 1;
        countdownBoard.textContent = countdown;
        if (countdown < 0) {
            timeUp = true
            countdown = 0;
            clearInterval(startCountdown);
            countdownBoard.textContent = 'Times UP!! Thank you for saving our planet!';
        }
    }, 1000);
}
startButton[0].addEventListener('click', startGame);

function whack(e){
    score++;
    this.style.backgroundImage = 'url("yoda.png")';
    this.style.pointerEvents = 'none';
    setTimeout(() => {
        this.backgroundImage = 'url("yoda.png")'
        this.pointerEvents = 'all';
    }, 800);
    scoreBoard.textContent = score;
}
moles.forEach(mole => mole.addEventListener('click', whack));