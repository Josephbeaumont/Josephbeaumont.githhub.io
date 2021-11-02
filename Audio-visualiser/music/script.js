
const container = document.getElementById('container');
const canvas = document.getElementById('canvas1');
canvas.width = window.innerWidth;
canvas.height = window.innerHeight;
const ctx = canvas.getContext('2d');
let audioSource;
let analyser;


container.addEventListener('click', function () {
    const audio1 = document.getElementById('audio1');
    const audioContext = new AudioContext;
    audio1.src = (Math.floor(Math.random() * 124))+".mp3" 
    audio1.play(); // 14, 25
    audioSource = audioContext.createMediaElementSource(audio1);
    analyser = audioContext.createAnalyser();
    audioSource.connect(analyser);
    analyser.connect(audioContext.destination);
    analyser.fftSize = 8192; 
    const bufferLength = analyser.frequencyBinCount;
    const dataArray = new Uint8Array(bufferLength);
// number of bars 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768;
    const barWidth = 0.800;
    let barHeight;
    let x = 0;

    function animate() {
        x = 0;
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        analyser.getByteFrequencyData(dataArray);
        drawVisualiser(bufferLength, x, barWidth, barHeight, dataArray);
        requestAnimationFrame(animate);
    }
    animate();
});

function drawVisualiser(bufferLength, x, barWidth, barHeight, dataArray){
    for (let i = 0; i < bufferLength; i++){
        ctx.save();
        barHeight = dataArray[i] * 2;
        ctx.translate(canvas.width/2, canvas.height/2);
        ctx.rotate(i * 20 / bufferLength);
        const hue = i / barHeight;
        ctx.fillStyle = 'hsl(' + hue + ',1000%, 50%)';
        ctx.fillRect(0, 0, barWidth, barHeight/1);
        x += barWidth;
        ctx.restore();
    }  
}

// Mouse particle Effects

    // canvas setup
    const canvas = document.getElementById('canvas2');
    const ctx = canvas.getContext('2d');
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
    const particleArray = [];

    // get mouse
    const mouse = {
        x: null,
        y: null
    }
    window.addEventListener('mousemove', function(event){
        mouse.x = event.x;
        mouse.y = event.y;
        //console.log(mouse.x, mouse.y);
    });
    setInterval(function(){
        mouse.x = undefined;
        mouse.y = undefined;
    }, 200);

    // Create Particles
    class Particle {
        constructor(x, y, size, color, weight){
            this.x =x;
            this.y =y;
            this.size = size;
            this.color = color;
            this.weight = weight;
        }
        draw(){
            ctx.beginPath();
            ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2, false);
            ctx.fillStyle = this.color;
            ctx.fill();
       }
        update(){
            this.size -= 0.05;
            if (this.size < 0){
                this.x = (mouse.x + ((Math.random() * 20) -10));
                this.y = (mouse.y + ((Math.random() * 20) -10));
                this.size = (Math.random() * 10) + 2;
                this.weight = (Math.random() * 2) - 0.5;
            }
            this.y += this.weight;
            this.weight += 0.2;

            if (this.y > canvas.height - this.size){
                this.weight *= -1;
            };
        }
   }
