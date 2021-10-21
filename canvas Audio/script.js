
const audioCtx = new AudioContext();
const container = document.getElementById('container1');
const canvas = document.getElementById('canvas1');
canvas.width = window.innerWidth;
canvas.height = window.innerHeight;
const ctx = canvas.getContext('2d');
let audioSource;
let analyser;

container.addEventListener('click', function(){
    const Audio1 = document.getElementById('Audio1');
    const Audio2 = document.getElementById('Audio2');
    Audio1.src ='music.mp3';
    Audio2.src ='Cinematic.mp3';
    Audio1.play();
    audioSource = audioContext.createMediaElementSource(Audio1);
    analyser = audioContext.createAnalyser();
    audioSource.connect(analyser);
    analyser.connect(audioContext.destination);
    analyser.fftSize = 64;
    const bufferLenght = analyser.frequencyBinCount;
    const dataArray = new Uint8Array(bufferLenght);

    const barWidth = canvas.width/bufferLenght;
    let barHeight;
    let x =0;

    function animate(){
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        analyser.getByteFrequencyData(dataArray);
        for (let i = 0; i< bufferLengh; i++){
            barHeight = dataArray[i];
            ctx.fillStyle = 'white';
            ctx.fillRect(x, canvas.height - barHeight, barWidth, barHeight);
            x += barWidth;


        }
        requestAnimationFrame(animate);
    }
    animate();
});
