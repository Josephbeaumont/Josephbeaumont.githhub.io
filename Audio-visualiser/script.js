
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
    audio1.src = "Audio visualiser/music/" + (Math.floor(Math.random() * 95))+".mp3" 
    audio1.play(); // 14, 25
    audioSource = audioContext.createMediaElementSource(audio1);
    analyser = audioContext.createAnalyser();
    audioSource.connect(analyser);
    analyser.connect(audioContext.destination);
    analyser.fftSize = 8192; 
    const bufferLength = analyser.frequencyBinCount;
    const dataArray = new Uint8Array(bufferLength);
// number of bars 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768;
    const barWidth = 0.20;
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
        ctx.fillStyle = 'hsl(' + hue + ',100%, 50%)';
        ctx.fillRect(0, 0, barWidth, barHeight/1);
        x += barWidth;
        ctx.restore();
    }  
}
