from insightface.app import FaceAnalysis

app = FaceAnalysis(name='buffalo_sc')
app.prepare(ctx_id=0, det_size=(640, 640))
print('embedding_size', app.face_model.embedding_size)
print('input_size', app.face_model.input_size)
print('face_model', app.face_model)
