// CLICK TO START MUSIC
// Music "Sirens" by H2x (Radik Khamatdinov) (http://h2xmusic.bandcamp.com)
// 3D software fluid cube music visualization
let { sin, cos, PI } = Math

visualizer.width = 2500
visualizer.height = 1000

// Creating canvas and getting 2d context
let c = document.querySelector('#visualizer').getContext('2d') 

// Getting canvas from context
let canvas = c.canvas

// Common
let frame = 0
let vertices = []
let cubeSize = 17

// Creating HTMLAudioElement
let audio = new Audio()
audio.crossOrigin = "anonymous"

// AudioContext, analyser and media element source, to make visualization
let ac
let an
let sr

// Spectrum array
let spectrumData
let spectrumRenderCount = 30 // How much lines of spectrum will render

// Rendering visualization
let loop = function () {
  let rad = frame / 2 / 180 * PI
  
  // Resize canvas
  if (canvas.width !== canvas.offsetWidth || canvas.height !== canvas.offsetHeight) {
      canvas.width = canvas.offsetWidth
      canvas.height = canvas.offsetHeight
  }
  
  frame++
  if (an) an.getByteFrequencyData(spectrumData)
  c.fillStyle = `hsl(${frame + 90}deg, 100%, 3%)`
  c.globalAlpha = 0.5
  c.fillRect(0, 0, canvas.width, canvas.height)
  c.globalAlpha = 1
  
  c.save()
  c.translate(canvas.width / 2, canvas.height / 2)
  
  for (let i = 0; i < vertices.length; i++) {
    let value = spectrumData ? spectrumData[i % spectrumRenderCount] / 10 + 1 : 0
    let vertex = vertices[i]
    let x = vertex[0]
    let y = vertex[1]
    let z = vertex[2]
    
    // Get distance to center
    let dist = cubeSize / 2 - Math.sqrt(x ** 2 + y ** 2 + z ** 2)
    
    // Making sphere by removing unnecessary vertices
    // if (dist < 0) continue // Uncomment here to make sphere
    
    // Rotation
    // Rotation Y
    let tx = x * cos(rad) + sin(rad) * z
    let tz = -x * sin(rad) + cos(rad) * z
    let ty = y
    
    // Apply transform
    x = tx
    y = ty
    z = tz
    
    // Rotation Z
    tx = x * cos(rad) - y * sin(rad)
    ty = x * sin(rad) + y * cos(rad)
    
    // Apply transform
    x = tx
    y = ty
    
    // Translate cube
    z -= 70
    
    // Make reaction on spectrum   
    z += value 
    y += value / 100
    
    // Distort animation
    x += Math.cos(frame / 20 + y / 5)
    y += Math.sin(frame / 20 + z / 3)
    
    // Make perspective
    x /= z / canvas.height / 2
    y /= z / canvas.height / 2
    
    // Drawing vertex
    c.fillStyle = `hsl(${dist / 16 * 360 + frame}deg, 100%, 50%)`
    c.fillRect(x - dist / 2, y - dist / 2, dist, dist)
  }
  
  c.restore()
  
  requestAnimationFrame(loop)
}

// Connecting analyser to audio
window.addEventListener('click', e => {
    audio.src =(Math.floor(Math.random() * 124)) + ".mp3"
    
  audio.oncanplaythrough = function () {
    ac = new AudioContext()
    sr = ac.createMediaElementSource(audio)
    an = ac.createAnalyser()

    spectrumData =  new Uint8Array(an.frequencyBinCount)

    // Setting analyser
    an.fftSize = 128
    an.smoothingTimeConstant = 0.9


    sr.connect(an)
    an.connect(ac.destination)
    
    audio.play()
  }
}, { once: true })

// Generation cube

for (let i = 0; i < cubeSize ** 3; i++) {
  let x = (i % cubeSize)
  let y = (i / cubeSize >> 0) % cubeSize
  let z = (i / cubeSize ** 2 >> 0)
  
  // Offset
  x -= cubeSize / 2 - 0.5
  y -= cubeSize / 2 - 0.5
  z -= cubeSize / 2 - 0.5
  
  vertices.push([x, y, z])
}
  
// Launch visualization
loop()
