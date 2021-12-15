var canvas = document.getElementById('canvas')
var ctx = canvas.getContext('2d')

canvas.width = innerWidth
canvas.height = innerHeight

var x = 125
var y = 5
var yspeed = 0
var xspeed = 0
var wpres = false 
var apres = false 
var spres = false 
var dpres = false 

document.addEventListener("keydown", keydownpress,false)
document.addEventListener('keyup', keyuppress, false)

setInterval(update,50)

function keydownpress(e) {
    if (e.key === "w"){
        wpres = true
    }
    if (e.key === "a"){
        apres = true
    }
    if (e.key === "s"){
        spres = true
    }
    if (e.key === "d"){
        dpres =  true
    }        
}

function keyuppress(e) {
    if (e.key === "w"){
        wpres = false
    }
    if (e.key === "a"){
        apres = false
    }
    if (e.key === "s"){
        spres = false
    }
    if(e.key === "d"){
        dpres = false
    }       
}

function update() {
    ctx.clearRect(0,0,1000,1000)
    x = x + xspeed
    y = y + yspeed

    ctx.fillRect(x, y, 100, 100)

    if(wpres === true){
        //yspeed = yspeed - 1
        y = y - 10	
    }
    if(apres === true){
        x = x - 10
    }
    if(spres === true){
        //yspeed = yspeed + 1
        y = y + 10
    }
    if(dpres === true){
        x = x + 10
    }
}

class Projectile {
    constructor(x, y, radius, color,
        velocity) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.color = color;
        this.velocity = velocity;
    }

    draw() {
        c.beginPath();
        c.arc(this.x, this.y, this.radius, 0, Math.PI * 2, false);
        c.fillStyle = this.color;
        c.fill();
    }
    update() {
        this.draw();
        this.x = this.x + this.velocity.x
        this.y = this.y + this.velocity.y
    }
}