const canvas = document.querySelector('canvas');
const ctx = canvas.getContext('2d');

canvas.width = innerWidth;
canvas.height = innerHeight;

const startGameBtn = document.querySelector('#startGameBtn')
const modalEl = document.querySelector('#modalEl')

document.addEventListener('keydown', keydown, false)
document.addEventListener('keyup', keyup, false)

var skull = new Image()
skull.src="skull.png"

var x = 0
var y = 0
var speedX = 0
var speedY = 0

var playerX = 5
var playerY = 3
var PspeedX = 0
var PspeedY = 0

var enemyX = 5
var enemyY = 3
var EspeedX = 0
var EspeedY = 0

var wpres = false
var apres = false
var spres = false
var dpres = false

let projectiles = []

function keydown(e) {
    if (e.key === 'w') {
        wpres = true
        //console.log('w')
    }
    if (e.key === 'a') {
        apres = true
        //console.log('a')
    }
    if (e.key === 's') {
        spres = true
        //console.log('s')
    }
    if (e.key === 'd') {
        dpres = true
        //console.log('d')
    }
}

function keyup(e) {
    if (e.key === 'w') {
        wpres = false
        //console.log('w')
    }
    if (e.key === 'a') {
        apres = false
       //console.log('a')
    }
    if (e.key === 's') {
        spres = false
        //console.log('s')
    }
    if (e.key === 'd') {
        dpres = false
        //console.log('d')
    }
}

class Player {
    
    draw() {
        ctx.beginPath()
        ctx.drawImage(skull,playerX,playerY,90,90)
        ctx.fill()
    }
}

class Projectile {
    constructor(playerX, playerY, radius, color, velocity) {
        this.x = playerX;
        this.y = playerY
        this.radius = radius
        this.color = color
        this.velocity = velocity
    }
    draw() {
        ctx.beginPath();
        ctx.arc(this.x, this.y, this.radius, 0, Math.PI * 2, false);
        ctx.fillStyle = this.color;
        ctx.fill();
    }
    update() {
        this.draw();
        this.x = this.x + this.velocity.x
        this.y = this.y + this.velocity.y
    }
}

class Enemy {
    constructor(x, y, radius, color,
        velocity) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.color = color;
        this.velocity = velocity;
    }

    draw() {
        ctx.beginPath();
        ctx.arc(this.x, this.y, this.radius, 0, Math.PI * 2, false);
        ctx.fillStyle = this.color;
        ctx.fill();
    }
    update() {
        this.draw();
        this.x = this.x + this.velocity.x
        this.y = this.y + this.velocity.y
    }
}



let player = new Player(playerX, playerY, 20, `hsl(${Math.random() * 360}, 50%, 50%)`);
let enemies = [];

function init() {
   player = new Player(playerX, playerY, 10, `hsl(${Math.random() * 360}, 100%, 50%)`);//new player calls on constructor
    enemies = [];
}

function spawnEnemies() {
    setInterval(() => {
        const radius = Math.random() * (30 - 4) + 4;

        let x
        let y

        if (Math.random() < 0.5) {
            x = Math.random() < 0.5 ? 0 - radius : canvas.
                width + radius
            y = Math.random() * canvas.height
        } else {
            x = Math.random() * canvas.width
            y = Math.random() < 0.5 ? 0 - radius : canvas.
                width + radius
        }

        const color = `hsl(${Math.random() * 360}, 50%, 50%)`;

        const angle = Math.atan2(
            canvas.height / 2 - y,
            canvas.width / 2 - x
        )
        const velocity = {
            x: Math.cos(angle),
            y: Math.sin(angle)
        }

        enemies.push(new Enemy(x, y, radius, color,
            velocity))
    }, 1000)
}

let animationId
function animate() {
    animationId = requestAnimationFrame(animate)
	ctx.clearRect(0, 0, 1980, 10000)
    player.draw();
	
	projectiles.forEach((projectile, index) => {
			projectile.update()

			if (projectile.x + projectile.radius < 0 ||
				projectile.x - projectile.radius > canvas.width ||
				projectile.y + projectile.radius < 0 ||
				projectile.y - projectile.radius > canvas.height
			) {
				/*setTimeout(() => {
					projectiles.splice(index, 1)
				}, 0)*/
			}
		})
	
   
    enemies.forEach((enemy, index) => {
        enemy.update();

        const dist = Math.hypot(player.x - enemy.x,
            player.y - enemy.y)

        if (dist - enemy.radius - player.radius < 1) { /*collision between enemy and player and ends game*/
            cancelAnimationFrame(animationId)  
        }
    });

	
	
		playerX = playerX 
		playerY = playerY 
		
		
		


		if (wpres === true) {
			playerY = playerY - 2
		}
		if (apres === true) {
			playerX = playerX - 2
		}
		if (spres === true) {
			playerY = playerY + 2
		}
		if (dpres === true) {
			playerX = playerX + 2
		}

		if (playerX > 1250) {
			playerX = 1250
		}
		if (playerX < 0) {
			playerX = 0
		}
		if (playerY > 500) {
			playerY = 500
		}
		if (playerY < 0) {
			playerY = 0
		}
		//console.log(playerX)
		//console.log(playerY)
	}

	addEventListener('click', (event) => {
		const angle = Math.atan2(
			event.clientY - canvas.height / 2,
			event.clientX - canvas.width / 2
		)
		const velocity = {
			x: Math.cos(angle) * 1,
			y: Math.sin(angle) * 1
		}
		projectiles.push(
			new Projectile(playerX + 45, playerY + 45,
				5, 'white', velocity)
		)
	})

startGameBtn.addEventListener('click', () => {
    init()
    animate();
    spawnEnemies();
    modalEl.style.display = 'none'
})

