canvas = document.getElementById('canvas')
ctx = canvas.getContext('2d')

document.addEventListener('keydown', keyPress, false)
document.addEventListener('keyup', keyRelease,false)


canvas.width = innerWidth / 2
canvas.height = innerHeight / 2

var skull = new Image()
skull.src="skull.png"
var X = 200
var Y = 400
var speedX = 0
var speedY = 0

setInterval(update, 1000)

// key binds
var wpress = false
var apress = false
var spress = false
var dpress = false


function keyPress(e) {
    if (e.key === "w"){
        wpress = true
    }
    if (e.key === "a"){
        apress = true
    }
    if (e.key === "s"){
        spress = true
    }
    if (e.key === "d"){
        dpress = true
    }
}

function keyRelease(e) {
    if (e.key === "w"){
        wpress = false
    }
    if (e.key === "a"){
        apress = false
    }
    if (e.key === "s"){
        spress = false
    }
    if (e.key === "d"){
        dpress = false
    }
}

function update() {
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(skull, 600, 100, 30, 45)

    X = X + speedX
    Y = Y + speedY

    if (wpress === true){
        Y = Y -1
    }

}