const canvas = document.getElementById('canvas1');
const ctx = canvas.getContext('2d');
canvas.width = window.innerWidth;
canvas.Height = window.innerHeight;
let particlesArray = [];

class Particle {
    constructor(x, y){
        this.x = x;
        this.y = y;
        this.size = 10;
        this.weight = 2;
        this.directionX - 1;
    }
    update(){
        this.weight += 0.1;
        this.y += this.weight;
    }
    draw(){
        ctx.fillStyle = 'red';
        ctx.beginPath();
        ctxarc(this.x, this.y, this.size, 0, Math.PI * 2);
        ctx.closePath();
        ctx.fill()
    }
}
    const particle1 = new Particle(100, 10);

    function animate(){
        ctx.fillStyle = 'rgba(255, 255, 255,0.01)';
        ctx.fillRect(0, 0, canvas.width, canvas.height)

        particle1.update();
        particle1.draw();
        requestAnimationFrame(animate);
    }
    animate();