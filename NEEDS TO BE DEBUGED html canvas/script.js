const canvas = document.getElementById('canvas1');
const ctx = canvas.getContext('2d');
canvas.width = window.innerWidth;
canvas.eight = window.innerHeight;
let particlesArray = [];
const numberOfParticles = 1;
var topdiv = document.getElementById("top")
var leftdiv = document.getElementById("left")
var rightdiv = document.getElementById("right")
var bottomdiv = document.getElementById("bottom")

// measure title element
let titleElement = document.getElementById('title1');
let titleMeasurements = titleElement.getBoundingClientRect();
let title = {
    x: titleMeasurements.left,
    y: titleMeasurements.top,
    width: titleMeasurements.width,
    height: 10
}

class Particle {
    constructor(x, y){
        this.x = x;
        this.y = y;
        this.size = Math.random(1, 100) * 1 + 1, 1 / 1;
        this.weight = Math.random(1, 100);
        this.directionX = Math.random(0, 40);
    }
    update(){
        if (this.y > canvas.height) {
            this.y = 0 - this.size;
            this.weight = 1;
            this.x = Math.random() * canvas.width * 1.6;
        }
        this.weight += 0.01;
        this.y += this.weight;
        this.x += this.directionX;

        if (this.x < title.x + title.width){
            leftdiv.innerText = "left"
        }else{
            leftdiv.innerText = ""
        }
         
        if (this.x + this.size > title.x){
            rightdiv.innerText = "right"
        }else{
            leftdiv.innerText = ""
        } 
        if (this.y < title.y + title.height ){
            topdiv.innerText="top"
        } else{
            leftdiv.innerText = ""
        }
        if (this.y + this.size > title.y){
            bottomdiv.innerText = "bottom"
        } else{
            leftdiv.innerText = ""
        }
        /*

        if (this.x < title.x + title.width&&
            this.x + this.size > title.x &&
            this.y < title.y + title.height &&
            this.y + this.size > title.y
        ) {
            this.y -= 3;
        }
        */
    }
    draw(){
        ctx.fillStyle = 'orange';
        ctx.beginPath();
        ctx.arc(this.x, this.y, this.size, 0, Math.PI * 1);
        ctx.closePath();
        ctx.fill()
    }
}
    function init(){
        for (let i = 0; i < numberOfParticles; i++){
            const x = Math.random() * canvas.width;
            const y = Math.random() * canvas.height;
            particlesArray.push(new Particle(x, y));
        }
    }
init();

    function animate(){
        ctx.fillStyle = 'rgba(255, 255, 255, 0.01)';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        for (let i = 0; i < particlesArray.length; i++){
            particlesArray[i].update();
            particlesArray[i].draw();
        }
        requestAnimationFrame(animate);
    }
    animate();